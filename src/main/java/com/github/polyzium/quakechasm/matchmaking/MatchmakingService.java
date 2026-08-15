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

package com.github.polyzium.quakechasm.matchmaking;

import com.github.polyzium.quakechasm.PluginConfig;
import com.github.polyzium.quakechasm.QuakePlugin;
import com.github.polyzium.quakechasm.QuakeUserState;
import com.github.polyzium.quakechasm.matchmaking.factory.MatchFactory;
import com.github.polyzium.quakechasm.matchmaking.map.QMap;
import com.github.polyzium.quakechasm.matchmaking.matches.Match;
import com.github.polyzium.quakechasm.matchmaking.matches.MatchPrivacy;
import com.github.polyzium.quakechasm.matchmaking.setup.GameSetup;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class MatchmakingService {
    public static MatchmakingService INSTANCE;

    private final QuakePlugin plugin;
    private final HashSet<String> warnedSetupProblems = new HashSet<>();
    private BukkitTask maintenanceTask;

    public MatchmakingService(QuakePlugin plugin) {
        this.plugin = plugin;
        INSTANCE = this;
    }

    public void start() {
        createConfiguredMatches();

        if (plugin.config.matchmaking.keepConfiguredMatchesReady) {
            maintenanceTask = new BukkitRunnable() {
                @Override
                public void run() {
                    int created = createConfiguredMatches();
                    if (created > 0 && plugin.config.matchmaking.joinsPlayersAutomatically()) {
                        autoJoinAvailablePlayers();
                    }
                }
            }.runTaskTimer(plugin, plugin.config.matchmaking.maintenanceIntervalTicks, plugin.config.matchmaking.maintenanceIntervalTicks);
        }

        if (plugin.config.matchmaking.joinsPlayersAutomatically()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                queueAutoJoin(player);
            }
        }
    }

    public void stop() {
        if (maintenanceTask != null) {
            maintenanceTask.cancel();
            maintenanceTask = null;
        }
    }

    public List<GameSetup> getSetups() {
        ArrayList<GameSetup> setups = new ArrayList<>();
        if (plugin.maps == null) {
            return setups;
        }

        for (QMap map : plugin.maps) {
            setups.add(getSetupForMap(map));
        }

        return setups;
    }

    public GameSetup getSetupForMap(QMap map) {
        PluginConfig.MatchmakingConfig matchmakingConfig = plugin.config.matchmaking;

        for (PluginConfig.GameSetupConfig setupConfig : matchmakingConfig.setups) {
            if (setupConfig.map == null) continue;
            if (setupConfig.map.equalsIgnoreCase(map.name)) {
                return GameSetup.fromConfig(map, setupConfig, matchmakingConfig);
            }
        }

        return GameSetup.fromMapDefault(map, matchmakingConfig);
    }

    public int createConfiguredMatches() {
        int created = 0;
        for (GameSetup setup : getSetups()) {
            if (!setup.shouldCreateOnStartup()) continue;
            if (setup.getMap().getMatch() != null) continue;

            Match match = createMatch(setup, null);
            if (match != null) {
                created++;
            }
        }

        if (created > 0) {
            plugin.getLogger().info("Created " + created + " configured matchmaking match(es)");
        }

        return created;
    }

    public Match createMatch(GameSetup setup, UUID ownerId) {
        if (setup.getMap().getMatch() != null) {
            return null;
        }

        if (!canCreateMatch(setup)) {
            return null;
        }

        MatchFactory matchFactory = setup.createFactory();
        if (matchFactory == null) {
            plugin.getLogger().warning("Invalid match mode in setup for map " + setup.getMap().name + ": " + setup.getMode());
            return null;
        }

        Match match = plugin.matchManager.newMatch(matchFactory, setup.getMap(), ownerId, setup.getPrivacy(), setup.getPassword());
        if (match == null) {
            return null;
        }

        match.setNeedPlayers(setup.getNeedPlayers());
        match.setScoreLimit(setup.getScoreLimit());
        return match;
    }

    private boolean canCreateMatch(GameSetup setup) {
        String problem = getSetupProblem(setup);
        if (problem == null) {
            return true;
        }

        String warningKey = setup.getMap().name + ":" + setup.getMode() + ":" + problem;
        if (warnedSetupProblems.add(warningKey)) {
            plugin.getLogger().warning(problem);
        }

        return false;
    }

    private String getSetupProblem(GameSetup setup) {
        QMap map = setup.getMap();

        return switch (GameSetup.normalizeMode(setup.getMode())) {
            case "debug", "ffa" -> {
                if (!map.hasSpawnpointFor(Team.FREE)) {
                    yield "Cannot create " + setup.getMode() + " match for map " + map.name + ": no free/team spawnpoints are available";
                }
                yield null;
            }
            case "tdm" -> getTeamSpawnpointProblem(map, setup.getMode());
            case "ctf" -> {
                String spawnpointProblem = getTeamSpawnpointProblem(map, setup.getMode());
                if (spawnpointProblem != null) {
                    yield spawnpointProblem;
                }
                if (!map.hasBaseCTFFlags()) {
                    yield "Cannot create CTF match for map " + map.name + ": one red and one blue base flag are required";
                }
                yield null;
            }
            default -> null;
        };
    }

    private String getTeamSpawnpointProblem(QMap map, String mode) {
        if (!map.hasSpawnpointFor(Team.RED) || !map.hasSpawnpointFor(Team.BLUE)) {
            return "Cannot create " + mode + " match for map " + map.name + ": red and blue spawnpoints are required";
        }

        return null;
    }

    public Match createMatch(
            String mode,
            int needPlayers,
            QMap map,
            UUID ownerId,
            MatchPrivacy privacy,
            String password
    ) {
        GameSetup fallback = getSetupForMap(map);
        GameSetup setup = new GameSetup(
                map,
                mode,
                needPlayers,
                fallback.getScoreLimit(),
                privacy,
                password,
                false,
                false,
                0
        );

        return createMatch(setup, ownerId);
    }

    public void queueAutoJoin(Player player) {
        if (!plugin.config.matchmaking.joinsPlayersAutomatically()) return;
        long delay = Math.max(1, plugin.config.matchmaking.autoJoinDelayTicks);

        new BukkitRunnable() {
            @Override
            public void run() {
                autoJoin(player);
            }
        }.runTaskLater(plugin, delay);
    }

    public void autoJoinAvailablePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            autoJoin(player);
        }
    }

    public boolean autoJoin(Player player) {
        if (!player.isOnline()) return false;
        if (!player.hasPermission("quake.player")) return false;

        QuakeUserState userState = plugin.userStates.get(player);
        if (userState == null || userState.currentMatch != null) {
            return false;
        }

        Match match = findBestAutoJoinMatch(player);
        if (match == null) {
            createConfiguredMatches();
            match = findBestAutoJoinMatch(player);
        }

        if (match == null) {
            return false;
        }

        try {
            match.join(player, null);
            return true;
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to auto-join " + player.getName() + " to match on " + match.getMap().name, e);
            return false;
        }
    }

    public Match findBestAutoJoinMatch(Player player) {
        return plugin.matchManager.matches.stream()
                .filter(match -> !match.matchEnding)
                .filter(match -> match.getPrivacy() == MatchPrivacy.PUBLIC)
                .filter(match -> match.canJoin(player, null))
                .filter(match -> {
                    GameSetup setup = getSetupForMap(match.getMap());
                    return setup.shouldAutoJoin()
                            && (!setup.hasPlayerLimit() || match.getPlayers().size() < setup.getMaxPlayers());
                })
                .min(Comparator.comparingInt(match -> match.getPlayers().size()))
                .orElse(null);
    }

    public Match findBestJoinableMatch(Player player) {
        return plugin.matchManager.getVisibleMatches(player).stream()
                .filter(match -> !match.matchEnding)
                .filter(match -> match.getPrivacy() == MatchPrivacy.PUBLIC)
                .filter(match -> match.canJoin(player, null))
                .min(Comparator.comparingInt(match -> match.getPlayers().size()))
                .orElse(null);
    }
}
