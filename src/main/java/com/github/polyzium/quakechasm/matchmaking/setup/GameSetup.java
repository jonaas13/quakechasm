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

package com.github.polyzium.quakechasm.matchmaking.setup;

import com.github.polyzium.quakechasm.PluginConfig;
import com.github.polyzium.quakechasm.matchmaking.factory.CTFMatchFactory;
import com.github.polyzium.quakechasm.matchmaking.factory.DebugMatchFactory;
import com.github.polyzium.quakechasm.matchmaking.factory.FFAMatchFactory;
import com.github.polyzium.quakechasm.matchmaking.factory.MatchFactory;
import com.github.polyzium.quakechasm.matchmaking.factory.TDMMatchFactory;
import com.github.polyzium.quakechasm.matchmaking.map.QMap;
import com.github.polyzium.quakechasm.matchmaking.matches.MatchMode;
import com.github.polyzium.quakechasm.matchmaking.matches.MatchPrivacy;

import java.util.Locale;

public class GameSetup {
    private final QMap map;
    private final String mode;
    private final int needPlayers;
    private final int scoreLimit;
    private final int timeLimitSeconds;
    private final MatchPrivacy privacy;
    private final String password;
    private final boolean createOnStartup;
    private final boolean autoJoin;
    private final int maxPlayers;

    public GameSetup(
            QMap map,
            String mode,
            int needPlayers,
            int scoreLimit,
            int timeLimitSeconds,
            MatchPrivacy privacy,
            String password,
            boolean createOnStartup,
            boolean autoJoin,
            int maxPlayers
    ) {
        this.map = map;
        this.mode = normalizeMode(mode);
        this.needPlayers = needPlayers;
        this.scoreLimit = scoreLimit;
        this.timeLimitSeconds = Math.max(0, timeLimitSeconds);
        this.privacy = privacy == null ? MatchPrivacy.PUBLIC : privacy;
        this.password = password;
        this.createOnStartup = createOnStartup;
        this.autoJoin = autoJoin;
        this.maxPlayers = Math.max(0, maxPlayers);
    }

    public static GameSetup fromMapDefault(QMap map, PluginConfig.MatchmakingConfig config) {
        String mode = config.getFallbackMode();
        if (map.recommendedModes != null && !map.recommendedModes.isEmpty()) {
            mode = map.recommendedModes.get(0).name().toLowerCase(Locale.ROOT);
        }

        int needPlayers = map.neededPlayers > 0 ? map.neededPlayers : config.defaultNeedPlayers;

        return new GameSetup(
                map,
                mode,
                needPlayers,
                config.defaultScoreLimit,
                config.defaultTimeLimitSeconds,
                MatchPrivacy.PUBLIC,
                null,
                config.createsMatchesAutomatically(),
                config.joinsPlayersAutomatically(),
                config.defaultMaxPlayers
        );
    }

    public static GameSetup fromConfig(QMap map, PluginConfig.GameSetupConfig setupConfig, PluginConfig.MatchmakingConfig config) {
        GameSetup fallback = fromMapDefault(map, config);

        return new GameSetup(
                map,
                firstNonBlank(setupConfig.mode, fallback.getMode()),
                setupConfig.needPlayers != null ? setupConfig.needPlayers : fallback.getNeedPlayers(),
                setupConfig.scoreLimit != null ? setupConfig.scoreLimit : fallback.getScoreLimit(),
                setupConfig.timeLimitSeconds != null ? setupConfig.timeLimitSeconds : fallback.getTimeLimitSeconds(),
                parsePrivacy(firstNonBlank(setupConfig.privacy, fallback.getPrivacy().name())),
                firstNonBlank(setupConfig.password, fallback.getPassword()),
                setupConfig.createOnStartup != null ? setupConfig.createOnStartup : fallback.shouldCreateOnStartup(),
                setupConfig.autoJoin != null ? setupConfig.autoJoin : fallback.shouldAutoJoin(),
                setupConfig.maxPlayers != null ? setupConfig.maxPlayers : fallback.getMaxPlayers()
        );
    }

    public QMap getMap() {
        return map;
    }

    public String getMode() {
        return mode;
    }

    public int getNeedPlayers() {
        return needPlayers;
    }

    public int getScoreLimit() {
        return scoreLimit;
    }

    public int getTimeLimitSeconds() {
        return timeLimitSeconds;
    }

    public MatchPrivacy getPrivacy() {
        return privacy;
    }

    public String getPassword() {
        return password;
    }

    public boolean shouldCreateOnStartup() {
        return createOnStartup;
    }

    public boolean shouldAutoJoin() {
        return autoJoin;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public boolean hasPlayerLimit() {
        return maxPlayers > 0;
    }

    public MatchFactory createFactory() {
        return factoryForMode(mode);
    }

    public static MatchFactory factoryForMode(String mode) {
        return switch (normalizeMode(mode)) {
            case "debug" -> new DebugMatchFactory();
            case "ffa" -> new FFAMatchFactory();
            case "tdm" -> new TDMMatchFactory();
            case "ctf" -> new CTFMatchFactory();
            default -> null;
        };
    }

    public static MatchMode asRecommendedMode(String mode) {
        try {
            return MatchMode.valueOf(normalizeMode(mode).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String normalizeMode(String mode) {
        if (mode == null) return "ffa";
        return mode.trim().toLowerCase(Locale.ROOT);
    }

    private static MatchPrivacy parsePrivacy(String privacy) {
        if (privacy == null) return MatchPrivacy.PUBLIC;

        try {
            return MatchPrivacy.valueOf(privacy.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return MatchPrivacy.PUBLIC;
        }
    }

    private static String firstNonBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
