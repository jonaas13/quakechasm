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

package com.github.polyzium.quakechasm.matchmaking.matches;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.jetbrains.annotations.NotNull;
import com.github.polyzium.quakechasm.QuakePlugin;
import com.github.polyzium.quakechasm.QuakeUserState;
import com.github.polyzium.quakechasm.game.combat.DamageCause;
import com.github.polyzium.quakechasm.game.combat.DeathMessages;
import com.github.polyzium.quakechasm.matchmaking.Team;
import com.github.polyzium.quakechasm.matchmaking.map.QMap;
import com.github.polyzium.quakechasm.misc.Chatroom;
import com.github.polyzium.quakechasm.misc.MiscUtil;
import com.github.polyzium.quakechasm.misc.Pair;
import com.github.polyzium.quakechasm.misc.TranslationManager;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;

public abstract class Match implements ForwardingAudience {
    protected QMap map;
    protected HashMap<Player, Team> players = new HashMap<>();
    protected Scoreboard vanillaScoreboard;
    protected Objective sidebarObjective;
    protected org.bukkit.scoreboard.Team vanillaRedTeam;
    protected org.bukkit.scoreboard.Team vanillaBlueTeam;
    private final ArrayList<String> sidebarEntries = new ArrayList<>();
    public boolean matchEnding = false;

    protected UUID ownerId;
    protected MatchPrivacy privacy;
    protected String passwordHash;
    protected HashSet<UUID> invitedPlayers;
    
    public Match(QMap map) {
        this(map, null, MatchPrivacy.PUBLIC, null);
    }
    
    public Match(QMap map, UUID ownerId, MatchPrivacy privacy, String password) {
        this.map = map;
        this.ownerId = ownerId;
        this.privacy = privacy != null ? privacy : MatchPrivacy.PUBLIC;
        this.invitedPlayers = new HashSet<>();
        
        if (password != null && !password.isEmpty()) {
            setPassword(password);
        }
        
        this.map.chunkLoad();

        this.vanillaScoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.sidebarObjective = this.vanillaScoreboard.registerNewObjective(
                "quake",
                Criteria.DUMMY,
                TranslationManager.t("scoreboard.title", TranslationManager.FALLBACK)
        );
        this.sidebarObjective.setDisplaySlot(DisplaySlot.SIDEBAR);

        // This is needed to hide nametags
        if (this.isTeamMatch()) {
            this.vanillaRedTeam = this.vanillaScoreboard.registerNewTeam("red");
            this.vanillaBlueTeam = this.vanillaScoreboard.registerNewTeam("blue");

            this.vanillaRedTeam.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, org.bukkit.scoreboard.Team.OptionStatus.FOR_OTHER_TEAMS);
            this.vanillaBlueTeam.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, org.bukkit.scoreboard.Team.OptionStatus.FOR_OTHER_TEAMS);
        }
    }
    public List<Player> getPlayers() {
        return this.players.keySet().stream().toList();
    }
    public List<Player> getPlayersInTeam(Team team) {
        return this.players.keySet().stream()
                .filter(player -> this.players.get(player) == team)
                .toList();
    }
    public QMap getMap() {
        return this.map;
    }
    public static String getNameStatic() {
        return "MATCH_BASE";
    }
    public String getNameKey() {
        return getNameStatic();
    }
    public void sendMessage(String message) {
        this.sendMessage(Component.text(message));
    }
    public Team getTeamOfPlayer(Player player) {
          return this.players.get(player);
    }
    // Implementation-dependent methods, use at your own discretion
    public abstract void setScoreLimit(int scoreLimit);
    public abstract void setNeedPlayers(int needPlayers);

    public void join(Player player, Team team) {
        Team resolvedTeam;
        if (team == null)
            resolvedTeam = this.assignTeam(player);
        else
            resolvedTeam = team;

        if (!this.allowedTeams().contains(resolvedTeam)) {
            QuakePlugin.INSTANCE.getLogger().warning("Player attempted to join disallowed team, ignoring");
            return;
        }

        QuakeUserState userState = QuakePlugin.INSTANCE.userStates.get(player);
        if (userState == null) {
            QuakePlugin.INSTANCE.getLogger().warning("Player attempted to join a match before their Quake state was initialized");
            return;
        }

        Location spawn = map.getRandomSpawnpoint(resolvedTeam);

        players.put(player, resolvedTeam);
        userState.currentMatch = this;

        player.teleport(spawn);
        MiscUtil.teleEffect(spawn, false);
        userState.initForMatch();

        // This is needed to hide nametags
        if (this.isTeamMatch()) {
            switch (this.players.get(player)) {
                case RED -> this.vanillaRedTeam.addPlayer(player);
                case BLUE -> this.vanillaBlueTeam.addPlayer(player);
                default -> throw new IllegalArgumentException("Attempt to add player of disallowed team to vanilla team");
            }
            setArmor(player, resolvedTeam);
        }

        player.setScoreboard(this.vanillaScoreboard);
        refreshPlayerListVisibility();

        userState.switchChat(Chatroom.MATCH);
        this.sendMessage(TranslationManager.t("match.player.joined", TranslationManager.FALLBACK,
            Placeholder.unparsed("player_name", player.getName())));
    }

    public void leave(Player player) {
        players.remove(player);
        cleanup(player);

        this.sendMessage(TranslationManager.t("match.player.left", TranslationManager.FALLBACK,
            Placeholder.unparsed("player_name", player.getName())));
    }
    public void end() {
        matchEnding = true;

        clearInvites();

        for (Player player : players.keySet()) {
            player.playSound(player, "quake.feedback.match_end", SoundCategory.NEUTRAL, 1, 1);
        }

        Match that = this;
        new BukkitRunnable() {
            int endTimer = 10;
            @Override
            public void run() {
                endTimer--;
                if (endTimer <= 5)
                    that.showTitle(Title.title(Component.empty(),
                        TranslationManager.t("match.end.teleportCountdown", TranslationManager.FALLBACK,
                            Placeholder.unparsed("count", String.valueOf(endTimer))),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(1))));

                if (endTimer == 0) {
                    for (Player player : players.keySet()) {
                        cleanup(player);
                    }

                    if (that.vanillaRedTeam != null) {
                        that.vanillaRedTeam.unregister();
                    }
                    if (that.vanillaBlueTeam != null) {
                        that.vanillaBlueTeam.unregister();
                    }

                    QuakePlugin.INSTANCE.matchManager.matches.remove(that);
                    if (QuakePlugin.INSTANCE.matchmakingService != null) {
                        QuakePlugin.INSTANCE.matchmakingService.onMatchEnded(that);
                    }

                    cancel();
                }
            }
        }.runTaskTimer(QuakePlugin.INSTANCE, 20, 20);
    }
    public abstract Team assignTeam(Player player);
    public static void setArmor(Player player, Team team) {
        ItemStack torso = new ItemStack(Material.LEATHER_CHESTPLATE);
        ItemStack pants = new ItemStack(Material.LEATHER_LEGGINGS);
        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        LeatherArmorMeta torsoMeta = (LeatherArmorMeta) torso.getItemMeta();
        LeatherArmorMeta pantsMeta = (LeatherArmorMeta) pants.getItemMeta();
        LeatherArmorMeta bootsMeta = (LeatherArmorMeta) boots.getItemMeta();
        torsoMeta.setColor(Color.fromRGB(Team.Colors.get(team)));
        pantsMeta.setColor(Color.fromRGB(Team.Colors.get(team)));
        bootsMeta.setColor(Color.fromRGB(Team.Colors.get(team)));
        torso.setItemMeta(torsoMeta);
        pants.setItemMeta(pantsMeta);
        boots.setItemMeta(bootsMeta);
        player.getInventory().setChestplate(torso);
        player.getInventory().setLeggings(pants);
        player.getInventory().setBoots(boots);
    }
    public void cleanup(Player player) {
        QuakeUserState userState = QuakePlugin.INSTANCE.userStates.get(player);
        if (userState != null) {
            userState.currentMatch = null;
            userState.switchChat(Chatroom.GLOBAL);
        }
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());

        // Restore normal movement speeds
        player.setWalkSpeed(QuakePlugin.INSTANCE.config.player.walkSpeed);

        MiscUtil.teleEffect(player.getLocation(), true);
        player.teleport(QuakePlugin.LOBBY);
        if (userState != null) {
            userState.reset();
        }

        refreshPlayerListVisibility();
        player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
    }
    public void onDeath(Player victim, Entity attacker, DamageCause cause) {
        QuakeUserState victimState = QuakePlugin.INSTANCE.userStates.get(victim);
        victimState.consecutiveRailgunHits = 0;
        victimState.lastKillTime = 0;

        if (attacker instanceof Player player && attacker != victim) {
            QuakeUserState attackerState = QuakePlugin.INSTANCE.userStates.get(player);
            attackerState.checkExcellentMedal();
            attackerState.lastKillTime = System.currentTimeMillis();
        }
    };
    public abstract List<Team> allowedTeams();
    public boolean isTeamMatch() {
        List<Team> allowedTeams = this.allowedTeams();
        return allowedTeams.contains(Team.RED) && allowedTeams.contains(Team.BLUE);
    };
    public static Component getDeathMessage(Player victim, Entity attacker, DamageCause cause, Locale locale) {
        // TODO Vault API for prefixes and shit
        Component component;
        if (attacker == null || victim == attacker) {
            String deathMsgKey = DeathMessages.SUICIDE.get(cause);
            if (deathMsgKey == null) {
                deathMsgKey = "obituary.suicide.unknown";
            }
            component = TranslationManager.t(deathMsgKey, locale, Placeholder.parsed("victim_name", victim.getName()), Placeholder.parsed("death_cause", cause.name()));
        } else {
            String deathMsgKey = DeathMessages.FRAG.get(cause);
            if (deathMsgKey == null) {
                deathMsgKey = "obituary.unknown";
            }
            component = TranslationManager.t(deathMsgKey, locale, Placeholder.parsed("victim_name", victim.getName()), Placeholder.parsed("attacker_name", attacker.getName()), Placeholder.parsed("death_cause", cause.name()));
        }

        return component.color(TextColor.color(0xff3f3f));
    }

    public Audience getTeamAudience(Team team) {
        return Audience.audience(this.getPlayersInTeam(team));
    }

    public @NotNull Iterable<? extends Audience> audiences() {
        return this.players.keySet();
    }
    
    public boolean isOwner(Player player) {
        if (this.ownerId == null) return false;
        return this.ownerId.equals(player.getUniqueId());
    }
    
    public boolean canManage(Player player) {
        return isOwner(player) || player.hasPermission("quake.admin");
    }
    
    public UUID getOwnerId() {
        return this.ownerId;
    }
    
    public void setOwner(UUID playerId) {
        this.ownerId = playerId;
    }
    
    public MatchPrivacy getPrivacy() {
        return this.privacy;
    }
    
    public void setPrivacy(MatchPrivacy privacy) {
        this.privacy = privacy;
    }
    
    public boolean isPasswordProtected() {
        return this.passwordHash != null;
    }
    
    public boolean checkPassword(String password) {
        if (this.passwordHash == null) return true;
        if (password == null) return false;
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return this.passwordHash.equals(bytesToHex(hash));
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }
    
    public void setPassword(String password) {
        if (password == null || password.isEmpty()) {
            this.passwordHash = null;
            return;
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            this.passwordHash = bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            QuakePlugin.INSTANCE.getLogger().severe("SHA-256 not available!");
            this.passwordHash = null;
        }
    }
    
    public void removePassword() {
        this.passwordHash = null;
    }
    
    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
    
    public boolean isInvited(UUID playerId) {
        return this.invitedPlayers.contains(playerId);
    }
    
    public void invitePlayer(UUID playerId) {
        this.invitedPlayers.add(playerId);
    }
    
    public void uninvitePlayer(UUID playerId) {
        this.invitedPlayers.remove(playerId);
    }
    
    public Set<UUID> getInvitedPlayers() {
        return new HashSet<>(this.invitedPlayers);
    }
    
    public void clearInvites() {
        this.invitedPlayers.clear();
    }

    
    public boolean canJoin(Player player, String password) {
        if (player.hasPermission("quake.admin")) {
            return true;
        }

        if (isOwner(player)) {
            return true;
        }

        return switch (this.privacy) {
            case PUBLIC -> true;
            case PASSWORD -> {
                if (isInvited(player.getUniqueId())) {
                    yield true;
                }
                yield checkPassword(password);
            }
            case INVITE_ONLY -> isInvited(player.getUniqueId());
            default -> false;
        };
    }

    public Map<String, Object> getManageableProperties() {
        Map<String, Object> properties = new HashMap<>();
        Class<?> clazz = this.getClass();
        
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(QManageable.class)) {
                QManageable annotation = field.getAnnotation(QManageable.class);
                try {
                    field.setAccessible(true);
                    properties.put(annotation.name(), field.get(this));
                } catch (IllegalAccessException e) {
                    QuakePlugin.INSTANCE.getLogger().warning(
                        "Failed to access manageable field: " + field.getName()
                    );
                }
            }
        }
        
        return properties;
    }

    public void setManageableProperty(String propertyName, Object value) throws IllegalArgumentException {
        Class<?> clazz = this.getClass();
        
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(QManageable.class)) {
                QManageable annotation = field.getAnnotation(QManageable.class);
                
                if (annotation.name().equals(propertyName)) {
                    try {
                        field.setAccessible(true);

                        if (value instanceof Integer) {
                            int intValue = (Integer) value;
                            if (intValue < annotation.min() || intValue > annotation.max()) {
                                throw new IllegalArgumentException(
                                    String.format("Value %d is out of range [%d, %d]",
                                        intValue, annotation.min(), annotation.max())
                                );
                            }
                        }
                        
                        field.set(this, value);
                        return;
                    } catch (IllegalAccessException e) {
                        throw new IllegalArgumentException("Cannot access property: " + propertyName);
                    }
                }
            }
        }
        
        throw new IllegalArgumentException("Property not found or not manageable: " + propertyName);
    }

    public Object getManageableProperty(String propertyName) throws IllegalArgumentException {
        Map<String, Object> properties = getManageableProperties();
        if (!properties.containsKey(propertyName)) {
            throw new IllegalArgumentException("Property not found or not manageable: " + propertyName);
        }
        return properties.get(propertyName);
    }

    public QManageable getPropertyAnnotation(String propertyName) {
        Class<?> clazz = this.getClass();
        
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(QManageable.class)) {
                QManageable annotation = field.getAnnotation(QManageable.class);
                if (annotation.name().equals(propertyName)) {
                    return annotation;
                }
            }
        }
        
        return null;
    }

    public static void refreshPlayerListVisibility() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Match viewerMatch = getCurrentMatch(viewer);

            for (Player target : Bukkit.getOnlinePlayers()) {
                Match targetMatch = getCurrentMatch(target);
                boolean shouldList = viewer.equals(target)
                        || (viewerMatch == null && targetMatch == null)
                        || (viewerMatch != null && viewerMatch == targetMatch);

                if (shouldList) {
                    viewer.listPlayer(target);
                } else {
                    viewer.unlistPlayer(target);
                }
            }
        }
    }

    private static Match getCurrentMatch(Player player) {
        QuakeUserState state = QuakePlugin.INSTANCE.userStates.get(player);
        return state == null ? null : state.currentMatch;
    }

    protected Component sidebarLine(String key, TagResolver... placeholders) {
        return TranslationManager.t(key, TranslationManager.FALLBACK, placeholders);
    }

    protected void updateSidebar(List<Component> lines) {
        this.sidebarObjective.displayName(TranslationManager.t("scoreboard.title", TranslationManager.FALLBACK));

        for (String entry : sidebarEntries) {
            this.vanillaScoreboard.resetScores(entry);
        }
        sidebarEntries.clear();

        int visibleLineCount = Math.min(lines.size(), 15);
        for (int i = 0; i < visibleLineCount; i++) {
            String entry = makeSidebarEntry(lines.get(i), i);
            sidebarEntries.add(entry);
            this.sidebarObjective.getScore(entry).setScore(visibleLineCount - i);
        }
    }

    private String makeSidebarEntry(Component line, int uniqueIndex) {
        String entry = LegacyComponentSerializer.legacySection().serialize(line);
        if (entry.isEmpty()) {
            entry = org.bukkit.ChatColor.values()[uniqueIndex % org.bukkit.ChatColor.values().length].toString();
        }

        int colorIndex = uniqueIndex;
        while (sidebarEntries.contains(entry)) {
            entry += org.bukkit.ChatColor.values()[colorIndex % org.bukkit.ChatColor.values().length];
            colorIndex++;
        }

        return entry;
    }
}
