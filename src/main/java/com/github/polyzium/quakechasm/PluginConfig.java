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
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    @SerializedName("gui")
    public GuiConfig gui;

    @SerializedName("scoreboard1")
    public LobbyScoreboardConfig scoreboard1;

    @SerializedName("teamSafeZone")
    public TeamSafeZoneConfig teamSafeZone;

    public static class LocaleConfig {
        @SerializedName("fallback")
        public String fallback = "en_US";

        @SerializedName("overrides")
        public LinkedHashMap<String, String> overrides = new LinkedHashMap<>();

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

        @SerializedName("fallbackMode")
        public String fallbackMode = "ffa";

        @Deprecated
        @SerializedName("defaultMode")
        public String defaultMode;

        @SerializedName("defaultNeedPlayers")
        public int defaultNeedPlayers = 2;

        @SerializedName("defaultScoreLimit")
        public int defaultScoreLimit = 10;

        @SerializedName("defaultTimeLimitSeconds")
        public int defaultTimeLimitSeconds = 300;

        @SerializedName("defaultMaxPlayers")
        public int defaultMaxPlayers = 50;

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

        public String getFallbackMode() {
            return firstNonBlank(fallbackMode, firstNonBlank(defaultMode, "ffa"));
        }
    }

    public static class GuiConfig {
        @SerializedName("matchBrowser")
        public MatchBrowserGuiConfig matchBrowser = new MatchBrowserGuiConfig();

        @SerializedName("scoreboard")
        public ScoreboardGuiConfig scoreboard = new ScoreboardGuiConfig();
    }

    public static class MatchBrowserGuiConfig {
        @SerializedName("minRows")
        public int minRows = 3;

        @SerializedName("maxRows")
        public int maxRows = 6;

        @SerializedName("publicMaterial")
        public String publicMaterial = "LIME_CONCRETE";

        @SerializedName("passwordMaterial")
        public String passwordMaterial = "YELLOW_CONCRETE";

        @SerializedName("inviteOnlyMaterial")
        public String inviteOnlyMaterial = "RED_CONCRETE";

        @SerializedName("emptyMaterial")
        public String emptyMaterial = "BARRIER";

        @SerializedName("fillerMaterial")
        public String fillerMaterial = "GRAY_STAINED_GLASS_PANE";

        @SerializedName("fillEmptySlots")
        public boolean fillEmptySlots = true;

        @SerializedName("showOwner")
        public boolean showOwner = true;

        @SerializedName("showScoreLimit")
        public boolean showScoreLimit = true;

        @SerializedName("forcePlainText")
        public boolean forcePlainText = true;
    }

    public static class ScoreboardGuiConfig {
        @SerializedName("enabled")
        public boolean enabled = true;

        @SerializedName("showMode")
        public boolean showMode = true;

        @SerializedName("showMap")
        public boolean showMap = true;

        @SerializedName("showScoreLimit")
        public boolean showScoreLimit = true;

        @SerializedName("splitWaitingPlayers")
        public boolean splitWaitingPlayers = true;

        @SerializedName("maxPlayerRows")
        public int maxPlayerRows = 8;
    }

    public static class LobbyScoreboardConfig {
        @SerializedName("title")
        public String title = " <gradient:#fa7f05:#f79514><bold>Minearchy</bold></gradient>";

        @SerializedName("lines")
        public ArrayList<String> lines = new ArrayList<>(List.of(
                " ",
                "&f🛡 %vault_prefix%",
                "&f👤 &f%player%",
                "&f🏷%vault_suffix%",
                " ",
                " <gradient:#fa7f05:#f79514>minearchy.com</gradient>"
        ));
    }

    public static class TeamSafeZoneConfig {
        @SerializedName("enabled")
        public boolean enabled = true;

        @SerializedName("radius")
        public double radius = 8.0;

        @SerializedName("message")
        public String message = "You can't enter the enemy safe zone.";
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

        @SerializedName("timeLimitSeconds")
        public Integer timeLimitSeconds;

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
        this.gui = new GuiConfig();
        this.scoreboard1 = new LobbyScoreboardConfig();
        this.teamSafeZone = new TeamSafeZoneConfig();
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

        Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
        String configJson = Files.readString(configFile.toPath(), StandardCharsets.UTF_8);
        JsonObject configRoot = JsonParser.parseString(configJson).getAsJsonObject();
        PluginConfig config = gson.fromJson(configRoot, PluginConfig.class);
        boolean changed = missingAny(configRoot, new String[][]{
                {"locale"},
                {"locale", "fallback"},
                {"locale", "overrides"},
                {"lobby"},
                {"lobby", "world"},
                {"lobby", "x"},
                {"lobby", "y"},
                {"lobby", "z"},
                {"lobby", "yaw"},
                {"lobby", "pitch"},
                {"player"},
                {"player", "walkSpeed"},
                {"matchmaking"},
                {"matchmaking", "mode"},
                {"matchmaking", "fallbackMode"},
                {"matchmaking", "defaultNeedPlayers"},
                {"matchmaking", "defaultScoreLimit"},
                {"matchmaking", "defaultTimeLimitSeconds"},
                {"matchmaking", "defaultMaxPlayers"},
                {"matchmaking", "autoJoinDelayTicks"},
                {"matchmaking", "keepConfiguredMatchesReady"},
                {"matchmaking", "maintenanceIntervalTicks"},
                {"matchmaking", "setups"},
                {"gui"},
                {"gui", "matchBrowser"},
                {"gui", "matchBrowser", "minRows"},
                {"gui", "matchBrowser", "maxRows"},
                {"gui", "matchBrowser", "publicMaterial"},
                {"gui", "matchBrowser", "passwordMaterial"},
                {"gui", "matchBrowser", "inviteOnlyMaterial"},
                {"gui", "matchBrowser", "emptyMaterial"},
                {"gui", "matchBrowser", "fillerMaterial"},
                {"gui", "matchBrowser", "fillEmptySlots"},
                {"gui", "matchBrowser", "showOwner"},
                {"gui", "matchBrowser", "showScoreLimit"},
                {"gui", "matchBrowser", "forcePlainText"},
                {"gui", "scoreboard"},
                {"gui", "scoreboard", "enabled"},
                {"gui", "scoreboard", "showMode"},
                {"gui", "scoreboard", "showMap"},
                {"gui", "scoreboard", "showScoreLimit"},
                {"gui", "scoreboard", "splitWaitingPlayers"},
                {"gui", "scoreboard", "maxPlayerRows"},
                {"scoreboard1"},
                {"scoreboard1", "title"},
                {"scoreboard1", "lines"},
                {"teamSafeZone"},
                {"teamSafeZone", "enabled"},
                {"teamSafeZone", "radius"},
                {"teamSafeZone", "message"}
        });
        boolean missingFallbackMode = isMissing(configRoot, new String[]{"matchmaking", "fallbackMode"});
        boolean hasLegacyDefaultMode = !isMissing(configRoot, new String[]{"matchmaking", "defaultMode"});

        if (config == null) {
            config = new PluginConfig();
            changed = true;
        }

        if (config.locale == null) {
            config.locale = new LocaleConfig();
            changed = true;
        }
        if (config.locale.overrides == null) {
            config.locale.overrides = new LinkedHashMap<>();
            changed = true;
        }
        if (config.lobby == null) {
            config.lobby = new LobbyConfig();
            changed = true;
        }
        if (config.player == null) {
            config.player = new PlayerConfig();
            changed = true;
        }
        if (config.matchmaking == null) {
            config.matchmaking = new MatchmakingConfig();
            changed = true;
        }
        if (config.matchmaking.setups == null) {
            config.matchmaking.setups = new ArrayList<>();
            changed = true;
        }
        String requestedFallbackMode = missingFallbackMode && hasLegacyDefaultMode
                ? config.matchmaking.defaultMode
                : config.matchmaking.fallbackMode;
        String fallbackMode = firstNonBlank(requestedFallbackMode, firstNonBlank(config.matchmaking.defaultMode, "ffa"));
        if (!fallbackMode.equals(config.matchmaking.fallbackMode)) {
            config.matchmaking.fallbackMode = fallbackMode;
            changed = true;
        }
        if (config.matchmaking.defaultMode != null) {
            config.matchmaking.defaultMode = null;
            changed = true;
        }
        if (config.matchmaking.defaultNeedPlayers < 1) {
            config.matchmaking.defaultNeedPlayers = 1;
            changed = true;
        }
        if (config.matchmaking.defaultScoreLimit < 1) {
            config.matchmaking.defaultScoreLimit = 1;
            changed = true;
        }
        if (config.matchmaking.defaultTimeLimitSeconds < 0) {
            config.matchmaking.defaultTimeLimitSeconds = 0;
            changed = true;
        }
        if (config.matchmaking.defaultMaxPlayers <= 0) {
            config.matchmaking.defaultMaxPlayers = 50;
            changed = true;
        }
        if (config.matchmaking.autoJoinDelayTicks < 1) {
            config.matchmaking.autoJoinDelayTicks = 1;
            changed = true;
        }
        if (config.matchmaking.maintenanceIntervalTicks < 20) {
            config.matchmaking.maintenanceIntervalTicks = 20;
            changed = true;
        }
        if (config.gui == null) {
            config.gui = new GuiConfig();
            changed = true;
        }
        if (config.gui.matchBrowser == null) {
            config.gui.matchBrowser = new MatchBrowserGuiConfig();
            changed = true;
        }
        if (config.gui.matchBrowser.minRows < 1) {
            config.gui.matchBrowser.minRows = 1;
            changed = true;
        }
        if (config.gui.matchBrowser.maxRows < config.gui.matchBrowser.minRows) {
            config.gui.matchBrowser.maxRows = config.gui.matchBrowser.minRows;
            changed = true;
        }
        if (config.gui.matchBrowser.maxRows > 6) {
            config.gui.matchBrowser.maxRows = 6;
            changed = true;
        }
        if (config.gui.scoreboard == null) {
            config.gui.scoreboard = new ScoreboardGuiConfig();
            changed = true;
        }
        if (config.gui.scoreboard.maxPlayerRows < 1) {
            config.gui.scoreboard.maxPlayerRows = 1;
            changed = true;
        }
        if (config.gui.scoreboard.maxPlayerRows > 12) {
            config.gui.scoreboard.maxPlayerRows = 12;
            changed = true;
        }
        if (config.scoreboard1 == null) {
            config.scoreboard1 = new LobbyScoreboardConfig();
            changed = true;
        }
        if (config.scoreboard1.title == null || config.scoreboard1.title.isBlank()) {
            config.scoreboard1.title = new LobbyScoreboardConfig().title;
            changed = true;
        }
        if (config.scoreboard1.lines == null) {
            config.scoreboard1.lines = new LobbyScoreboardConfig().lines;
            changed = true;
        }
        LobbyScoreboardConfig lobbyScoreboardDefaults = new LobbyScoreboardConfig();
        if (" <#fa7f05>&lMinearchy</#f79514>".equals(config.scoreboard1.title)) {
            config.scoreboard1.title = lobbyScoreboardDefaults.title;
            changed = true;
        }
        for (int i = 0; i < config.scoreboard1.lines.size(); i++) {
            if (" <#fa7f05>minearchy.com</#f79514>".equals(config.scoreboard1.lines.get(i))) {
                config.scoreboard1.lines.set(i, lobbyScoreboardDefaults.lines.getLast());
                changed = true;
            }
        }
        if (config.teamSafeZone == null) {
            config.teamSafeZone = new TeamSafeZoneConfig();
            changed = true;
        }
        if (config.teamSafeZone.radius < 0) {
            config.teamSafeZone.radius = 0;
            changed = true;
        }
        if (config.teamSafeZone.message == null || config.teamSafeZone.message.isBlank()) {
            config.teamSafeZone.message = new TeamSafeZoneConfig().message;
            changed = true;
        }

        if (changed) {
            try (FileWriter writer = new FileWriter(configFile, StandardCharsets.UTF_8)) {
                gson.toJson(config, writer);
            }
            QuakePlugin.INSTANCE.getLogger().info("Configuration updated with missing defaults");
        }

        INSTANCE = config;
        QuakePlugin.INSTANCE.getLogger().info("Configuration loaded successfully");
        return config;
    }

    private static boolean missingAny(JsonObject root, String[][] paths) {
        for (String[] path : paths) {
            if (isMissing(root, path)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isMissing(JsonObject root, String[] path) {
        JsonObject current = root;
        for (int i = 0; i < path.length; i++) {
            if (current == null || !current.has(path[i])) {
                return true;
            }

            if (i < path.length - 1) {
                if (!current.get(path[i]).isJsonObject()) {
                    return true;
                }
                current = current.getAsJsonObject(path[i]);
            }
        }

        return false;
    }

    private static String firstNonBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        return value;
    }
}
