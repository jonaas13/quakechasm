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
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.jetbrains.annotations.NotNull;
import com.github.polyzium.quakechasm.PluginConfig;
import com.github.polyzium.quakechasm.QuakePlugin;
import com.github.polyzium.quakechasm.QuakeUserState;
import com.github.polyzium.quakechasm.game.combat.DamageCause;
import com.github.polyzium.quakechasm.game.combat.DeathMessages;
import com.github.polyzium.quakechasm.matchmaking.Team;
import com.github.polyzium.quakechasm.matchmaking.map.QMap;
import com.github.polyzium.quakechasm.matchmaking.map.Spawnpoint;
import com.github.polyzium.quakechasm.misc.Chatroom;
import com.github.polyzium.quakechasm.misc.MiscUtil;
import com.github.polyzium.quakechasm.misc.Pair;
import com.github.polyzium.quakechasm.misc.ScoreboardText;
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
    private static final int MAX_SIDEBAR_LINES = 15;
    private static final int STATIC_BRANDING_LINES = 1;
    public boolean matchEnding = false;

    @QManageable(name = "timeLimitSeconds", min = 0, max = 3600, description = "Match time limit in seconds, 0 disables the timer")
    protected int timeLimitSeconds = 300;
    private BukkitTask matchTimerTask = null;
    private int remainingTimeSeconds = -1;

    protected UUID ownerId;
    protected MatchPrivacy privacy;
    protected String passwordHash;
    protected HashSet<UUID> invitedPlayers;
    @QManageable(name = "maxPlayers", min = 0, max = 500, description = "Maximum players allowed in the match, 0 disables the limit")
    protected int maxPlayers = 0;
    
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

    public void setTimeLimitSeconds(int timeLimitSeconds) {
        this.timeLimitSeconds = Math.max(0, timeLimitSeconds);
        if (hasStarted()) {
            startMatchTimer();
        }
    }

    public boolean hasStarted() {
        return false;
    }

    public boolean startNow() {
        return false;
    }

    protected boolean hasWarmupPhase() {
        return false;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = Math.max(0, maxPlayers);
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public boolean hasPlayerLimit() {
        return maxPlayers > 0;
    }

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

        teleportToMatchSpawn(player, spawn);
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

        userState.switchChat(getDefaultGameChatroom());
        retryBedrockPostJoinTeleport(player, spawn);
        if (hasWarmupPhase() && !hasStarted()) {
            broadcastWarmupJoin(player);
        } else {
            broadcastJoin(player);
        }
    }

    public void leave(Player player) {
        players.remove(player);
        cleanup(player);

        this.sendMessage(TranslationManager.t("match.player.left", TranslationManager.FALLBACK,
            Placeholder.unparsed("player_name", player.getName())));
    }
    public void end() {
        if (matchEnding) {
            return;
        }
        matchEnding = true;
        stopMatchTimer();

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
                    boolean autoJoinNextMatch = shouldKeepChunksLoadedForAutoJoin();
                    for (Player player : new ArrayList<>(players.keySet())) {
                        cleanup(player);
                        if (autoJoinNextMatch && QuakePlugin.INSTANCE.matchmakingService != null) {
                            QuakePlugin.INSTANCE.matchmakingService.queueAutoJoin(player);
                        }
                    }

                    if (that.vanillaRedTeam != null) {
                        that.vanillaRedTeam.unregister();
                    }
                    if (that.vanillaBlueTeam != null) {
                        that.vanillaBlueTeam.unregister();
                    }

                    QuakePlugin.INSTANCE.matchManager.matches.remove(that);
                    that.map.cleanup(!that.shouldKeepChunksLoadedForAutoJoin());
                    if (QuakePlugin.INSTANCE.matchmakingService != null) {
                        QuakePlugin.INSTANCE.matchmakingService.onMatchEnded(that);
                    }

                    cancel();
                }
            }
        }.runTaskTimer(QuakePlugin.INSTANCE, 20, 20);
    }
    public abstract Team assignTeam(Player player);

    private void teleportToMatchSpawn(Player player, Location spawn) {
        map.preparePlayerTeleport(player, spawn);
        player.teleport(spawn);
        map.refreshPlayerTeleport(player, spawn);

        if (MiscUtil.isBedrockPlayer(player)) {
            retryBedrockMatchTeleport(player, spawn);
        }
    }

    private void retryBedrockMatchTeleport(Player player, Location spawn) {
        Match match = this;
        new BukkitRunnable() {
            private int attempts = 0;

            @Override
            public void run() {
                QuakeUserState userState = QuakePlugin.INSTANCE.userStates.get(player);
                if (!player.isOnline() || userState == null || userState.currentMatch != match || match.hasStarted() || attempts >= 3) {
                    cancel();
                    return;
                }

                map.preparePlayerTeleport(player, spawn);
                player.teleport(spawn);
                map.refreshPlayerTeleport(player, spawn);
                player.setFallDistance(0);
                player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                attempts++;
            }
        }.runTaskTimer(QuakePlugin.INSTANCE, 5, 10);
    }

    private void retryBedrockPostJoinTeleport(Player player, Location spawn) {
        if (!MiscUtil.isBedrockPlayer(player)) {
            return;
        }

        Match match = this;
        new BukkitRunnable() {
            @Override
            public void run() {
                QuakeUserState userState = QuakePlugin.INSTANCE.userStates.get(player);
                if (!player.isOnline() || userState == null || userState.currentMatch != match || match.hasStarted()) {
                    return;
                }

                map.preparePlayerTeleport(player, spawn);
                player.teleport(spawn);
                map.refreshPlayerTeleport(player, spawn);
                player.setFallDistance(0);
                player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            }
        }.runTaskLater(QuakePlugin.INSTANCE, 20);
    }

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

        // Restore normal movement speeds
        player.setWalkSpeed(QuakePlugin.INSTANCE.config.player.walkSpeed);

        MiscUtil.teleEffect(player.getLocation(), true);
        Location cleanupDestination = getCleanupDestination(player);
        if (isMapDestination(cleanupDestination)) {
            map.preparePlayerTeleport(player, cleanupDestination);
        }
        player.teleport(cleanupDestination);
        if (isMapDestination(cleanupDestination)) {
            map.refreshPlayerTeleport(player, cleanupDestination);
        }
        if (userState != null) {
            userState.reset();
        }
        if (isLobbyDestination(cleanupDestination) && QuakePlugin.INSTANCE.lobbyScoreboard != null) {
            QuakePlugin.INSTANCE.lobbyScoreboard.apply(player);
        }

        refreshPlayerListVisibility();
        player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
    }

    private Location getCleanupDestination(Player player) {
        return QuakePlugin.LOBBY;
    }

    private boolean shouldKeepChunksLoadedForAutoJoin() {
        return QuakePlugin.INSTANCE.matchmakingService != null
                && QuakePlugin.INSTANCE.config.matchmaking.keepConfiguredMatchesReady
                && QuakePlugin.INSTANCE.config.matchmaking.joinsPlayersAutomatically();
    }

    private boolean isLobbyDestination(Location location) {
        if (location == null || QuakePlugin.LOBBY == null) {
            return false;
        }

        return Objects.equals(location.getWorld(), QuakePlugin.LOBBY.getWorld())
                && location.distanceSquared(QuakePlugin.LOBBY) < 0.0001;
    }

    private boolean isMapDestination(Location location) {
        return location != null && location.getWorld() == this.map.world && this.map.bounds.contains(location.toVector());
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

    public boolean isEnemySafeZone(Player player, Location location) {
        if (!isTeamMatch()) {
            return false;
        }

        Team playerTeam = getTeamOfPlayer(player);
        if (playerTeam == null) {
            return false;
        }

        Team enemyTeam = switch (playerTeam) {
            case RED -> Team.BLUE;
            case BLUE -> Team.RED;
            default -> null;
        };

        return enemyTeam != null && isInsideTeamSafeZone(enemyTeam, location);
    }

    public boolean isInsideTeamSafeZone(Team team, Location location) {
        PluginConfig.TeamSafeZoneConfig config = QuakePlugin.INSTANCE.config.teamSafeZone;
        if (config == null || !config.enabled || config.radius <= 0 || location == null) {
            return false;
        }

        double radiusSquared = config.radius * config.radius;
        List<Spawnpoint> spawnpoints = map.getStrictSpawnpointsFor(team);
        for (Spawnpoint spawnpoint : spawnpoints) {
            if (spawnpoint.pos == null || spawnpoint.pos.getWorld() != location.getWorld()) {
                continue;
            }

            double dx = location.getX() - spawnpoint.pos.getX();
            double dz = location.getZ() - spawnpoint.pos.getZ();
            if (dx * dx + dz * dz <= radiusSquared) {
                return true;
            }
        }

        return false;
    }

    protected void broadcastWarmupJoin(Player player) {
        broadcastJoin(player);
    }

    private void broadcastJoin(Player player) {
        String maxPlayersText = hasPlayerLimit() ? String.valueOf(maxPlayers) : "unlimited";
        String mode = TranslationManager.tLegacy(getNameKey(), TranslationManager.FALLBACK);
        sendMessage("§8[§cQuake§8] §f" + player.getName()
                + " joined " + mode
                + " on " + map.getDisplayName()
                + " §7(" + players.size() + "/" + maxPlayersText + " players)");
    }

    private Chatroom getDefaultGameChatroom() {
        return Chatroom.MATCH;
    }

    public static Component getDeathMessage(Player victim, Entity attacker, DamageCause cause, Locale locale) {
        // TODO Vault API for prefixes and shit
        DamageCause resolvedCause = cause == null ? DamageCause.UNKNOWN : cause;
        Component component;
        if (attacker == null || victim == attacker) {
            String deathMsgKey = DeathMessages.SUICIDE.get(resolvedCause);
            if (deathMsgKey == null) {
                deathMsgKey = "obituary.suicide.unknown";
            }
            component = TranslationManager.t(deathMsgKey, locale, Placeholder.parsed("victim_name", victim.getName()), Placeholder.parsed("death_cause", resolvedCause.name()));
        } else {
            String deathMsgKey = DeathMessages.FRAG.get(resolvedCause);
            if (deathMsgKey == null) {
                deathMsgKey = "obituary.unknown";
            }
            component = TranslationManager.t(deathMsgKey, locale, Placeholder.parsed("victim_name", victim.getName()), Placeholder.parsed("attacker_name", attacker.getName()), Placeholder.parsed("death_cause", resolvedCause.name()));
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
        if (hasPlayerLimit() && !players.containsKey(player) && players.size() >= maxPlayers) {
            return false;
        }

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

        while (clazz != null && Match.class.isAssignableFrom(clazz)) {
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
            clazz = clazz.getSuperclass();
        }
        
        return properties;
    }

    public void setManageableProperty(String propertyName, Object value) throws IllegalArgumentException {
        Class<?> clazz = this.getClass();

        while (clazz != null && Match.class.isAssignableFrom(clazz)) {
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
                            if ("timeLimitSeconds".equals(propertyName) && value instanceof Integer intValue) {
                                setTimeLimitSeconds(intValue);
                            }
                            return;
                        } catch (IllegalAccessException e) {
                            throw new IllegalArgumentException("Cannot access property: " + propertyName);
                        }
                    }
                }
            }
            clazz = clazz.getSuperclass();
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

        while (clazz != null && Match.class.isAssignableFrom(clazz)) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(QManageable.class)) {
                    QManageable annotation = field.getAnnotation(QManageable.class);
                    if (annotation.name().equals(propertyName)) {
                        return annotation;
                    }
                }
            }
            clazz = clazz.getSuperclass();
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

    protected PluginConfig.ScoreboardGuiConfig scoreboardConfig() {
        return QuakePlugin.INSTANCE.config.gui.scoreboard;
    }

    protected void startMatchTimer() {
        stopMatchTimer();
        if (timeLimitSeconds <= 0) {
            remainingTimeSeconds = -1;
            onMatchTimerTick();
            return;
        }

        remainingTimeSeconds = timeLimitSeconds;
        onMatchTimerTick();
        matchTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (matchEnding) {
                    cancel();
                    return;
                }

                remainingTimeSeconds--;
                onMatchTimerTick();
                if (remainingTimeSeconds <= 0) {
                    matchTimerTask = null;
                    cancel();
                    onTimeLimitReached();
                }
            }
        }.runTaskTimer(QuakePlugin.INSTANCE, 20, 20);
    }

    protected void stopMatchTimer() {
        if (matchTimerTask != null) {
            matchTimerTask.cancel();
            matchTimerTask = null;
        }
    }

    protected boolean hasTimeLimit() {
        return timeLimitSeconds > 0;
    }

    protected int getRemainingTimeSeconds() {
        return remainingTimeSeconds;
    }

    protected String getFormattedTimeRemaining() {
        if (remainingTimeSeconds < 0) {
            return formatTime(timeLimitSeconds);
        }

        return formatTime(remainingTimeSeconds);
    }

    public static String formatTime(int seconds) {
        if (seconds <= 0) {
            return "none";
        }

        int minutes = seconds / 60;
        int remainder = seconds % 60;
        return String.format(Locale.ROOT, "%d:%02d", minutes, remainder);
    }

    protected void onMatchTimerTick() {
    }

    protected void onTimeLimitReached() {
        end();
    }

    protected void updateSidebar(List<Component> lines) {
        PluginConfig.ScoreboardGuiConfig config = scoreboardConfig();
        if (!config.enabled) {
            this.vanillaScoreboard.clearSlot(DisplaySlot.SIDEBAR);
            return;
        }

        this.sidebarObjective.setDisplaySlot(DisplaySlot.SIDEBAR);
        this.sidebarObjective.displayName(TranslationManager.t("scoreboard.title", TranslationManager.FALLBACK));

        for (String entry : sidebarEntries) {
            this.vanillaScoreboard.resetScores(entry);
        }
        sidebarEntries.clear();

        int dynamicLineCount = Math.min(lines.size(), MAX_SIDEBAR_LINES - STATIC_BRANDING_LINES);
        ArrayList<Component> visibleLines = new ArrayList<>(lines.subList(0, dynamicLineCount));
        visibleLines.add(centerSidebarLine(sidebarLine("scoreboard.website"), visibleLines));

        int visibleLineCount = visibleLines.size();
        for (int i = 0; i < visibleLineCount; i++) {
            String entry = makeSidebarEntry(visibleLines.get(i), i);
            sidebarEntries.add(entry);
            this.sidebarObjective.getScore(entry).setScore(visibleLineCount - i);
        }
    }

    private Component centerSidebarLine(Component line, List<Component> referenceLines) {
        PlainTextComponentSerializer plainText = PlainTextComponentSerializer.plainText();
        int lineLength = plainText.serialize(line).length();
        int targetLength = lineLength;
        for (Component referenceLine : referenceLines) {
            targetLength = Math.max(targetLength, plainText.serialize(referenceLine).length());
        }

        int padding = Math.max(0, (targetLength - lineLength) / 2);
        if (padding == 0) {
            return line;
        }

        return Component.text(" ".repeat(padding)).append(line);
    }

    private String makeSidebarEntry(Component line, int uniqueIndex) {
        String entry = ScoreboardText.serialize(line);
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
