/*
 * Quakechasm, a Quake minigame plugin for Minecraft servers running PaperMC
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

package com.github.polyzium.quakechasm.events.listeners;

import com.github.polyzium.quakechasm.PluginConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.util.Vector;
import com.github.polyzium.quakechasm.QuakePlugin;
import com.github.polyzium.quakechasm.QuakeUserState;
import com.github.polyzium.quakechasm.game.combat.DamageCause;
import com.github.polyzium.quakechasm.game.movement.StrafeJumpHandler;
import com.github.polyzium.quakechasm.matchmaking.matches.Match;
import com.github.polyzium.quakechasm.misc.MiscUtil;

import static com.github.polyzium.quakechasm.game.combat.WeaponUtil.damageCustom;

public class MiscListener implements Listener {
    private static final long SAFE_ZONE_WARNING_COOLDOWN_MS = 1500;

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joinedPlayer = event.getPlayer();
        QuakePlugin.INSTANCE.initPlayer(joinedPlayer);
        Match.refreshPlayerListVisibility();

        if (QuakePlugin.INSTANCE.matchmakingService != null
                && QuakePlugin.INSTANCE.config.matchmaking.joinsPlayersAutomatically()
                && QuakePlugin.INSTANCE.matchmakingService.autoJoin(joinedPlayer)) {
            return;
        }

        joinedPlayer.teleport(QuakePlugin.LOBBY);
        if (QuakePlugin.INSTANCE.lobbyScoreboard != null) {
            QuakePlugin.INSTANCE.lobbyScoreboard.apply(joinedPlayer);
        }
        if (QuakePlugin.INSTANCE.matchmakingService != null) {
            QuakePlugin.INSTANCE.matchmakingService.queueAutoJoin(joinedPlayer);
        }
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        QuakeUserState userState = QuakePlugin.INSTANCE.userStates.get(player);
        if (userState != null && userState.currentMatch != null) userState.currentMatch.leave(player);
        if (QuakePlugin.INSTANCE.matchmakingService != null) {
            QuakePlugin.INSTANCE.matchmakingService.removePendingAutoJoin(player);
        }

        QuakePlugin.INSTANCE.userStates.remove(player);
        Match.refreshPlayerListVisibility();

        player.teleport(QuakePlugin.LOBBY);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        // Set fall damage to always deal 2 hearts no matter what (repro of -10 HP on fall in Quake)
        if (!(event.getEntityType() == EntityType.PLAYER && event.getCause() == EntityDamageEvent.DamageCause.FALL)) return;
        if (event.getEntity().getFallDistance() < 10)
            event.setCancelled(true);
        event.setDamage(2);
    }

    // Strafejumping
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Immobilize when match is ending (prevents walking speed bypass hacks)
        QuakeUserState userState = QuakePlugin.INSTANCE.userStates.get(player);
        if (userState == null) return;
        if (userState.currentMatch != null && userState.currentMatch.matchEnding) {
            holdPosition(event);
            return;
        }
        if (blockEnemySafeZone(event, userState)) {
            return;
        }

        userState.strafeJumpTicks++;
        if (MiscUtil.isBedrockPlayer(player)) {
            return;
        }

        Vector velocity = event.getTo().toVector().subtract(event.getFrom().toVector());
        StrafeJumpHandler.applyStrafeAcceleration(player, userState, velocity);
    }

    private boolean blockEnemySafeZone(PlayerMoveEvent event, QuakeUserState userState) {
        if (userState.currentMatch == null) {
            return false;
        }

        PluginConfig.TeamSafeZoneConfig config = QuakePlugin.INSTANCE.config.teamSafeZone;
        if (config == null || !config.enabled || config.radius <= 0) {
            return false;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (!userState.currentMatch.isEnemySafeZone(event.getPlayer(), to)) {
            return false;
        }

        if (userState.currentMatch.isEnemySafeZone(event.getPlayer(), from)) {
            return false;
        }

        holdPosition(event);
        warnSafeZone(event.getPlayer(), userState, config.message);
        return true;
    }

    private void holdPosition(PlayerMoveEvent event) {
        Location from = event.getFrom().clone();
        Location to = event.getTo();
        from.setYaw(to.getYaw());
        from.setPitch(to.getPitch());
        event.setTo(from);
    }

    private void warnSafeZone(Player player, QuakeUserState userState, String message) {
        long now = System.currentTimeMillis();
        if (now - userState.lastSafeZoneWarningMillis < SAFE_ZONE_WARNING_COOLDOWN_MS) {
            return;
        }

        userState.lastSafeZoneWarningMillis = now;
        player.sendActionBar(Component.text(message).color(TextColor.color(0xff5555)));
    }

    // Telefrag: kill any entity at the teleport destination
    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player teleportingPlayer = event.getPlayer();

        for (Entity entity : event.getTo().getWorld().getNearbyEntities(event.getTo(), 1.5, 2.0, 1.5)) {
            if (entity.equals(teleportingPlayer)) continue;
            if (!(entity instanceof LivingEntity victim)) continue;

            damageCustom(victim, 1000, teleportingPlayer, DamageCause.TELEFRAG);
        }
    }

}
