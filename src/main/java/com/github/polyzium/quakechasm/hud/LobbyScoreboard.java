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

package com.github.polyzium.quakechasm.hud;

import com.github.polyzium.quakechasm.PluginConfig;
import com.github.polyzium.quakechasm.QuakePlugin;
import com.github.polyzium.quakechasm.misc.ScoreboardText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LobbyScoreboard {
    private static final int MAX_SIDEBAR_LINES = 15;
    private static final Pattern HEX_SPAN = Pattern.compile("<#([0-9a-fA-F]{6})>(.*?)</#([0-9a-fA-F]{6})>");
    private static final Pattern LEGACY_HEX_CODE = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern LEGACY_CODE = Pattern.compile("&([0-9a-fk-orA-FK-OR])");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();

    public void apply(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        PluginConfig.LobbyScoreboardConfig config = QuakePlugin.INSTANCE.config.scoreboard1;
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(
                "quake_lobby",
                Criteria.DUMMY,
                format(player, config.title)
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        Set<String> entries = new HashSet<>();
        int lineCount = Math.min(config.lines.size(), MAX_SIDEBAR_LINES);
        for (int i = 0; i < lineCount; i++) {
            String entry = makeEntry(format(player, config.lines.get(i)), entries, i);
            objective.getScore(entry).setScore(lineCount - i);
        }

        player.setScoreboard(scoreboard);
    }

    private String makeEntry(Component line, Set<String> entries, int uniqueIndex) {
        String entry = ScoreboardText.serialize(line);
        if (entry.isEmpty()) {
            entry = ChatColor.values()[uniqueIndex % ChatColor.values().length].toString();
        }

        int colorIndex = uniqueIndex;
        while (entries.contains(entry)) {
            entry += ChatColor.values()[colorIndex % ChatColor.values().length];
            colorIndex++;
        }
        entries.add(entry);

        return entry;
    }

    private Component format(Player player, String raw) {
        if (raw == null) {
            raw = "";
        }

        String resolved = resolvePlaceholders(player, raw);
        String miniMessage = legacyCodesToMiniMessage(legacyHexCodesToMiniMessage(hexSpansToGradient(resolved.replace('§', '&'))));

        try {
            return MINI_MESSAGE.deserialize(miniMessage);
        } catch (RuntimeException e) {
            return LEGACY_AMPERSAND.deserialize(resolved);
        }
    }

    private String resolvePlaceholders(Player player, String raw) {
        String resolved = raw.replace("%player%", player.getName());
        resolved = applyPlaceholderApi(player, resolved);

        if (resolved.contains("%vault_prefix%")) {
            resolved = resolved.replace("%vault_prefix%", getVaultChatValue(player, "getPlayerPrefix"));
        }
        if (resolved.contains("%vault_suffix%")) {
            resolved = resolved.replace("%vault_suffix%", getVaultChatValue(player, "getPlayerSuffix"));
        }

        return resolved
                .replace("%vault_prefix%", "")
                .replace("%vault_suffix%", "");
    }

    private String applyPlaceholderApi(Player player, String text) {
        try {
            Class<?> placeholderApi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method setPlaceholders = placeholderApi.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
            Object value = setPlaceholders.invoke(null, player, text);
            return value instanceof String stringValue ? stringValue : text;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return text;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private String getVaultChatValue(Player player, String methodName) {
        try {
            Class<?> chatClass = Class.forName("net.milkbowl.vault.chat.Chat");
            RegisteredServiceProvider registration = Bukkit.getServicesManager().getRegistration((Class) chatClass);
            if (registration == null) {
                return "";
            }

            Object chat = registration.getProvider();
            return invokeVaultChatMethod(chat, methodName, player);
        } catch (ClassNotFoundException e) {
            return "";
        }
    }

    private String invokeVaultChatMethod(Object chat, String methodName, Player player) {
        Class<?> targetClass = chat.getClass();
        Object value = invokeIfPresent(targetClass, chat, methodName, new Class<?>[]{Player.class}, new Object[]{player});
        if (value != null) {
            return value.toString();
        }

        value = invokeIfPresent(
                targetClass,
                chat,
                methodName,
                new Class<?>[]{String.class, OfflinePlayer.class},
                new Object[]{player.getWorld().getName(), player}
        );
        if (value != null) {
            return value.toString();
        }

        value = invokeIfPresent(
                targetClass,
                chat,
                methodName,
                new Class<?>[]{String.class, String.class},
                new Object[]{player.getWorld().getName(), player.getName()}
        );

        return value == null ? "" : value.toString();
    }

    private Object invokeIfPresent(Class<?> targetClass, Object target, String methodName, Class<?>[] parameterTypes, Object[] args) {
        try {
            Method method = targetClass.getMethod(methodName, parameterTypes);
            return method.invoke(target, args);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }

    private String hexSpansToGradient(String input) {
        Matcher matcher = HEX_SPAN.matcher(input);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement("<gradient:#" + matcher.group(1) + ":#" + matcher.group(3) + ">" + matcher.group(2) + "</gradient>")
            );
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String legacyHexCodesToMiniMessage(String input) {
        Matcher matcher = LEGACY_HEX_CODE.matcher(input);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement("<#" + matcher.group(1) + ">"));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String legacyCodesToMiniMessage(String input) {
        Matcher matcher = LEGACY_CODE.matcher(input);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(legacyCodeToMiniMessage(matcher.group(1).charAt(0))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String legacyCodeToMiniMessage(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> "";
        };
    }
}
