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

package com.github.polyzium.quakechasm;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Locale;

public class PluginConfig {
    public static PluginConfig INSTANCE = null;

    @SerializedName("locale")
    public LocaleConfig locale;

    @SerializedName("lobby")
    public LobbyConfig lobby;

    @SerializedName("player")
    public PlayerConfig player;

    @SerializedName("matchmaking")
    public MatchmakingConfig matchmaking;

    public static class LocaleConfig {
        @SerializedName("fallback")
        public String fallback = "en_US";

        public Locale getFallbackLocale() {
            String[] parts = fallback.split("_");
            if (parts.length == 2) {
                return new Locale(parts[0], parts[1]);
            }
            return new Locale(parts[0]);
        }
    }

    public static class LobbyConfig {
        @SerializedName("world")
        public String world = "world";

        @SerializedName("x")
        public double x = 0.0;

        @SerializedName("y")
        public double y = 64.0;

        @SerializedName("z")
        public double z = 0.0;

        @SerializedName("yaw")
        public float yaw = 0.0f;

        @SerializedName("pitch")
        public float pitch = 0.0f;
    }

    public static class PlayerConfig {
        @SerializedName("walkSpeed")
        public float walkSpeed = 0.4f;
    }

    public static class MatchmakingConfig {
        @SerializedName("mode")
        public String mode = "manual";

        @SerializedName("defaultMode")
        public String defaultMode = "ffa";

        @SerializedName("defaultNeedPlayers")
        public int defaultNeedPlayers = 2;

        @SerializedName("defaultScoreLimit")
        public int defaultScoreLimit = 10;

        @SerializedName("defaultMaxPlayers")
        public int defaultMaxPlayers = 0;

        @SerializedName("autoJoinDelayTicks")
        public long autoJoinDelayTicks = 5;

        @SerializedName("keepConfiguredMatchesReady")
        public boolean keepConfiguredMatchesReady = true;

        @SerializedName("maintenanceIntervalTicks")
        public long maintenanceIntervalTicks = 200;

        @SerializedName("setups")
        public ArrayList<GameSetupConfig> setups = new ArrayList<>();

        public boolean createsMatchesAutomatically() {
            return mode != null && (mode.equalsIgnoreCase("startup") || mode.equalsIgnoreCase("autoplay"));
        }

        public boolean joinsPlayersAutomatically() {
            return mode != null && mode.equalsIgnoreCase("autoplay");
        }
    }

    public static class GameSetupConfig {
        @SerializedName("map")
        public String map;

        @SerializedName("mode")
        public String mode;

        @SerializedName("needPlayers")
        public Integer needPlayers;

        @SerializedName("scoreLimit")
        public Integer scoreLimit;

        @SerializedName("privacy")
        public String privacy;

        @SerializedName("password")
        public String password;

        @SerializedName("createOnStartup")
        public Boolean createOnStartup;

        @SerializedName("autoJoin")
        public Boolean autoJoin;

        @SerializedName("maxPlayers")
        public Integer maxPlayers;
    }

    public PluginConfig() {
        this.locale = new LocaleConfig();
        this.lobby = new LobbyConfig();
        this.player = new PlayerConfig();
        this.matchmaking = new MatchmakingConfig();
    }

    public static PluginConfig load() throws IOException {
        File dataFolder = QuakePlugin.INSTANCE.getDataFolder();
        if (!dataFolder.exists()) {
            if (!dataFolder.mkdir()) {
                QuakePlugin.INSTANCE.getLogger().severe("Unable to create data folder " + dataFolder);
                throw new IOException("Unable to create data folder");
            }
        }

        File configFile = new File(dataFolder, "config.json");
        if (!configFile.exists()) {
            QuakePlugin.INSTANCE.getLogger().info("Config file not found, copying default from JAR resources");

            try (InputStream defaultConfig = QuakePlugin.INSTANCE.getResource("config.json")) {
                if (defaultConfig == null) {
                    QuakePlugin.INSTANCE.getLogger().severe("Default config.json not found in JAR resources");
                    throw new IOException("Default config.json not found in JAR resources");
                }
                
                Files.copy(defaultConfig, configFile.toPath());
                QuakePlugin.INSTANCE.getLogger().info("Default config.json copied to " + configFile.getPath());
            }
        }

        try (FileReader reader = new FileReader(configFile)) {
            Gson gson = new Gson();
            PluginConfig config = gson.fromJson(reader, PluginConfig.class);

            if (config.locale == null) config.locale = new LocaleConfig();
            if (config.lobby == null) config.lobby = new LobbyConfig();
            if (config.player == null) config.player = new PlayerConfig();
            if (config.matchmaking == null) config.matchmaking = new MatchmakingConfig();
            if (config.matchmaking.setups == null) config.matchmaking.setups = new ArrayList<>();
            if (config.matchmaking.defaultNeedPlayers < 1) config.matchmaking.defaultNeedPlayers = 1;
            if (config.matchmaking.defaultScoreLimit < 1) config.matchmaking.defaultScoreLimit = 1;
            if (config.matchmaking.defaultMaxPlayers < 0) config.matchmaking.defaultMaxPlayers = 0;
            if (config.matchmaking.autoJoinDelayTicks < 1) config.matchmaking.autoJoinDelayTicks = 1;
            if (config.matchmaking.maintenanceIntervalTicks < 20) config.matchmaking.maintenanceIntervalTicks = 20;
            
            INSTANCE = config;
            QuakePlugin.INSTANCE.getLogger().info("Configuration loaded successfully");
            return config;
        }
    }
}
