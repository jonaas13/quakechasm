/*
 * Quakechasm, a Quake minigame plugin for Minecraft servers running Spigot
 * 
 * Copyright (C) 2024-present Polyzium
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.polyzium.quakechasm;

import com.github.polyzium.quakechasm.game.combat.DamageData;
import com.github.polyzium.quakechasm.game.combat.MedalType;
import com.github.polyzium.quakechasm.game.combat.WeaponType;
import com.github.polyzium.quakechasm.game.combat.WeaponUserState;
import com.github.polyzium.quakechasm.game.combat.WeaponUtil;
import com.github.polyzium.quakechasm.game.entities.Trigger;
import com.github.polyzium.quakechasm.game.entities.triggers.Jumppad;
import com.github.polyzium.quakechasm.game.mapper.PortalTool;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import com.github.polyzium.quakechasm.game.combat.powerup.Powerup;
import com.github.polyzium.quakechasm.game.combat.powerup.PowerupType;
import com.github.polyzium.quakechasm.hud.Hud;
import com.github.polyzium.quakechasm.matchmaking.Team;
import com.github.polyzium.quakechasm.matchmaking.matches.Match;
import com.github.polyzium.quakechasm.misc.Chatroom;
import com.github.polyzium.quakechasm.misc.MiscUtil;
import com.github.polyzium.quakechasm.misc.TranslationManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

public class QuakeUserState {
    public Player player;
    public WeaponUserState weaponState;
    public Location portalLoc = null;
    public BukkitRunnable healthDecreaser;
    public BukkitRunnable armorDecreaser;
    public int armor = 0;
    public ArrayList<Powerup> activePowerups = new ArrayList<>(3);
    public Hud hud;
    public Match currentMatch;
    public DamageData lastDamage;
    public Chatroom currentChat = Chatroom.GLOBAL;

    // Strafe jump state
    public int strafeJumpTicks = 0;

    // Medal tracking
    public HashMap<MedalType, Integer> medals = new HashMap<>();
    public long lastKillTime = 0;
    public int consecutiveRailgunHits = 0;
    public boolean lastKillWasMidair = false;
    
    // Medal display queue
    private MedalType currentlyDisplayedMedal = null;
    private BossBar currentMedalBossbar = null;
    private BukkitRunnable currentMedalTimer = null;
    private Queue<MedalType> medalQueue = new LinkedList<>();

    // Mapper toolkit state
    public Trigger movingEntity = null;
    public boolean holdingEntityTool = false;

    // Jumppad tool state
    public Location jumppadPlacementLoc = null;
    public Jumppad editingJumppad = null;
    public boolean settingLandingPos = false;
    public double jumppadPowerMultiplier = 1.0;

    // Portal tool state
    public PortalTool.PortalToolData portalToolData = new PortalTool.PortalToolData();

    public QuakeUserState(Player player) {
        this.player = player;
        this.weaponState = new WeaponUserState();
        this.hud = new Hud(this);
    }

    public Player getPlayer() {
        return player;
    }

    public void reset() {
        this.weaponState = new WeaponUserState();
        this.armor = 0;
        for (Powerup activePowerup : this.activePowerups) {
            activePowerup.timer.cancel();
        }
        this.activePowerups.clear();
        this.hud.powerupBoard.update();
        this.player.setHealth(20);
        this.player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20);
        this.player.getInventory().clear();
        
        // Reset medal tracking
        this.medals.clear();
        this.lastKillTime = 0;
        this.consecutiveRailgunHits = 0;
        this.lastKillWasMidair = false;
        
        // Clear medal display queue
        if (currentMedalBossbar != null) {
            QuakePlugin.INSTANCE.adventure().player(player).hideBossBar(currentMedalBossbar);
            currentMedalBossbar = null;
        }
        if (currentMedalTimer != null) {
            currentMedalTimer.cancel();
            currentMedalTimer = null;
        }
        currentlyDisplayedMedal = null;
        medalQueue.clear();
    }

    public void initForMatch() {
        this.reset();
        this.initRespawn();
    }

    public void initRespawn() {
        // Clear all ammo
        Arrays.fill(weaponState.ammo, 0);

        ItemStack machinegun = new ItemStack(Material.CARROT_ON_A_STICK);
        ItemMeta mgMeta = machinegun.getItemMeta();
        mgMeta.setCustomModelData(WeaponType.MACHINEGUN);
        Component displayName = TranslationManager.t("pickup.weapon.machinegun", this.player)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.byBoolean(false));
        mgMeta.setDisplayName(LegacyComponentSerializer.legacySection().serialize(displayName));
        machinegun.setItemMeta(mgMeta);

        weaponState.ammo[WeaponType.MACHINEGUN] = WeaponUtil.DEFAULT_AMMO[WeaponType.MACHINEGUN];

        player.getInventory().addItem(machinegun);
        player.getInventory().setHeldItemSlot(0);

        // Set health to 125 Quake HP
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(25);
        player.setHealth(25);
        this.startHealthDecreaser();
    }
    
    public Location prepareRespawn() {
        this.initRespawn();
        return this.currentMatch.getMap().getRandomSpawnpoint(this.currentMatch.getTeamOfPlayer(this.player));
    }

    public void respawn() {
        Location spawnpoint = this.prepareRespawn();
        player.teleport(spawnpoint);
        MiscUtil.teleEffect(spawnpoint, false);

        if (this.currentMatch.isTeamMatch())
            Match.setArmor(this.player, this.currentMatch.getTeamOfPlayer(this.player));
    }

    public void switchChat(Chatroom chatroom) {
        if (
                this.currentMatch == null &&
                (chatroom == Chatroom.MATCH || chatroom == Chatroom.TEAM)
        ) {
            QuakePlugin.INSTANCE.adventure().player(player).sendMessage(TranslationManager.t("error.chat.switchNoMatch.title", player,
                    Placeholder.component("chatroom", TranslationManager.t("error.chat.switchNoMatch." + chatroom.name().toLowerCase() + "Adj", player).color(TextColor.color(chatroom.getColor())))
            ));
            return;
        }

        if (
                this.currentMatch != null &&
                        this.currentMatch.allowedTeams().stream().allMatch(team -> team == Team.FREE) &&
                        chatroom == Chatroom.TEAM
        ) {
            QuakePlugin.INSTANCE.adventure().player(player).sendMessage(TranslationManager.t("error.match.notTeam", player));
            return;
        }

        this.currentChat = chatroom;
        QuakePlugin.INSTANCE.adventure().player(player).sendMessage(TranslationManager.t("command.chat.switch.title", player,
                Placeholder.component("chatroom", TranslationManager.t("command.chat.switch." + this.currentChat.name().toLowerCase() + "Adj", player).color(TextColor.color(this.currentChat.getColor())))
        ));
    }

    public void startArmorDecreaser() {
        if (this.armorDecreaser != null) return;

        this.armorDecreaser = new BukkitRunnable() {
            @Override
            public void run() {
                if (armor <= 100) {
                    this.cancel();
                    armorDecreaser = null;
                    return;
                }
                armor -= 1;
            }
        };
        armorDecreaser.runTaskTimer(QuakePlugin.INSTANCE, 20, 20);
    }

    public void startHealthDecreaser() {
        if (this.healthDecreaser != null) return;

        this.healthDecreaser = new BukkitRunnable() {
            @Override
            public void run() {
                // Fix NullPointerException on logout
                try {
                    if (Powerup.hasPowerup(player, PowerupType.REGENERATION)) return;
                } catch (NullPointerException e) {
                    cancel();
                }

                double currentHealth = player.getHealth();
                if (currentHealth <= 20) {
                    player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20);
                    this.cancel();
                    healthDecreaser = null;
                    return;
                }
                float newHealth = Math.round((currentHealth * 5) - 1) / 5f;
                player.setHealth(newHealth);
                player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(newHealth);
            }
        };
        healthDecreaser.runTaskTimer(QuakePlugin.INSTANCE, 20, 20);
    }

    public void awardMedal(MedalType medalType) {
        int count = medals.getOrDefault(medalType, 0) + 1;
        medals.put(medalType, count);

        String medalText = TranslationManager.tLegacy(medalType.getTranslationKey(), player) + " x" + count;

        QuakePlugin.INSTANCE.adventure().player(player).sendMessage(TranslationManager.t("game.medal.awarded", player,
            Placeholder.unparsed("medal_text", medalText)).color(TextColor.color(0xFFD700)));

        if (currentlyDisplayedMedal == medalType) {
            updateCurrentMedalDisplay(medalType, count);
            resetMedalTimer();
        }
        else if (currentlyDisplayedMedal != null) {
            if (!medalQueue.contains(medalType)) {
                medalQueue.offer(medalType);
            }
        }
        else {
            showMedalBossbar(medalType, count);
        }
    }
    
    private void showMedalBossbar(MedalType medalType, int count) {
        currentlyDisplayedMedal = medalType;

        Component bossbarTitle;
        if (count >= 10) {
            bossbarTitle = Component.join(JoinConfiguration.noSeparators(),
                            Component.text(medalType.getIcon()),
                            Component.text(count).color(TextColor.color(0xffffff))
                    )
                    .font(net.kyori.adventure.key.Key.key("minecraft:hud_bossbar"));
        } else {
            bossbarTitle = Component.text(Character.toString(medalType.getIcon()).repeat(count))
                    .font(net.kyori.adventure.key.Key.key("minecraft:hud_bossbar"));
        }
        
        currentMedalBossbar = BossBar.bossBar(
                bossbarTitle,
                0,
                BossBar.Color.YELLOW,
                BossBar.Overlay.PROGRESS
        );
        
        QuakePlugin.INSTANCE.adventure().player(player).showBossBar(currentMedalBossbar);

        currentMedalTimer = new BukkitRunnable() {
            @Override
            public void run() {
                QuakePlugin.INSTANCE.adventure().player(player).hideBossBar(currentMedalBossbar);
                currentMedalBossbar = null;
                currentlyDisplayedMedal = null;
                currentMedalTimer = null;

                if (!medalQueue.isEmpty()) {
                    MedalType nextMedal = medalQueue.poll();
                    int nextCount = medals.getOrDefault(nextMedal, 0);
                    showMedalBossbar(nextMedal, nextCount);
                }
            }
        };
        currentMedalTimer.runTaskLater(QuakePlugin.INSTANCE, 60);
    }
    
    private void updateCurrentMedalDisplay(MedalType medalType, int count) {
        if (currentMedalBossbar != null) {
            Component bossbarTitle;
            if (count >= 10) {
                bossbarTitle = Component.join(JoinConfiguration.noSeparators(),
                                Component.text(medalType.getIcon()),
                                Component.text(count).color(TextColor.color(0xffffff))
                        )
                        .font(net.kyori.adventure.key.Key.key("minecraft:hud_bossbar"));
            } else {
                bossbarTitle = Component.text(Character.toString(medalType.getIcon()).repeat(count))
                        .font(net.kyori.adventure.key.Key.key("minecraft:hud_bossbar"));
            }
            
            currentMedalBossbar.name(bossbarTitle);
        }
    }
    
    private void resetMedalTimer() {
        if (currentMedalTimer != null) {
            currentMedalTimer.cancel();

            currentMedalTimer = new BukkitRunnable() {
                @Override
                public void run() {
                    QuakePlugin.INSTANCE.adventure().player(player).hideBossBar(currentMedalBossbar);
                    currentMedalBossbar = null;
                    currentlyDisplayedMedal = null;
                    currentMedalTimer = null;

                    if (!medalQueue.isEmpty()) {
                        MedalType nextMedal = medalQueue.poll();
                        int nextCount = medals.getOrDefault(nextMedal, 0);
                        showMedalBossbar(nextMedal, nextCount);
                    }
                }
            };
            currentMedalTimer.runTaskLater(QuakePlugin.INSTANCE, 60);
        }
    }

    public void checkExcellentMedal() {
        long currentTime = System.currentTimeMillis();
        
        if (lastKillTime != 0 && (currentTime - lastKillTime) <= 2000) {
            awardMedal(MedalType.EXCELLENT);
        }
        
        lastKillTime = currentTime;
    }

    public void checkImpressiveMedal() {
        if (consecutiveRailgunHits >= 2)
            awardMedal(MedalType.IMPRESSIVE);
    }
}
