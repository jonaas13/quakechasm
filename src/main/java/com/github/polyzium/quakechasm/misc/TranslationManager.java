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

package com.github.polyzium.quakechasm.misc;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import com.github.polyzium.quakechasm.QuakePlugin;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class TranslationManager {
    public static Locale FALLBACK = Locale.US;
    private final HashMap<String, JsonObject> translations;
    private final MiniMessage miniMessage;
    public static TranslationManager INSTANCE = null;

    public TranslationManager() throws IOException {
        FALLBACK = QuakePlugin.INSTANCE.config.locale.getFallbackLocale();
        
        this.translations = new HashMap<>();
        this.miniMessage = MiniMessage.miniMessage();

        loadLocale(FALLBACK);
        
        INSTANCE = this;
    }

    private void loadLocale(Locale locale) throws IOException {
        String localeString = locale.toString();
        String filename = "lang_" + localeString + ".json";
        
        JsonObject localeData = null;

        File configFolder = QuakePlugin.INSTANCE.getDataFolder();
        File localeFile = new File(configFolder, filename);
        
        if (localeFile.exists()) {
            QuakePlugin.INSTANCE.getLogger().info("Loading locale " + localeString + " from config folder: " + localeFile.getPath());
            try (FileReader reader = new FileReader(localeFile)) {
                localeData = new Gson().fromJson(reader, JsonObject.class);
            } catch (Exception e) {
                QuakePlugin.INSTANCE.getLogger().warning("Failed to load locale from config folder: " + e.getMessage());
                QuakePlugin.INSTANCE.getLogger().warning("Falling back to JAR resource");
            }
        }

        if (localeData == null) {
            InputStream resourceStream = QuakePlugin.INSTANCE.getResource(filename);
            if (resourceStream != null) {
                QuakePlugin.INSTANCE.getLogger().info("Loading locale " + localeString + " from JAR resources");
                String jsonContent = new String(resourceStream.readAllBytes(), StandardCharsets.UTF_8);
                localeData = new Gson().fromJson(jsonContent, JsonObject.class);
                resourceStream.close();
            } else {
                throw new IOException("Locale file not found: " + filename);
            }
        }

        translations.put(localeString, localeData);

        translations.put(locale.getLanguage(), localeData);
    }

    private String getTranslationString(String key, Locale locale) {
        String override = getOverride(key);
        if (override != null) {
            return override;
        }

        String localeString = locale.toString();
        if (!translations.containsKey(localeString) && !translations.containsKey(locale.getLanguage())) {
            try {
                loadLocale(locale);
            } catch (IOException e) {
                QuakePlugin.INSTANCE.getLogger().warning("Failed to load locale " + localeString + ": " + e.getMessage());
            }
        }

        JsonObject localeTranslations = translations.get(localeString);

        if (localeTranslations == null) {
            localeTranslations = translations.get(locale.getLanguage());
        }

        if (localeTranslations == null) {
            localeTranslations = translations.get(FALLBACK.toString());
            if (localeTranslations == null) {
                localeTranslations = translations.get(FALLBACK.getLanguage());
            }
        }

        String[] keyParts = key.split("\\.");
        JsonElement current = localeTranslations;

        for (String part : keyParts) {
            if (current == null || !current.isJsonObject()) {
                break;
            }
            current = current.getAsJsonObject().get(part);
        }

        if (current != null && current.isJsonPrimitive()) {
            return current.getAsString();
        }

        if (!localeString.equals(FALLBACK.toString()) && !locale.getLanguage().equals(FALLBACK.getLanguage())) {
            JsonObject fallbackTranslations = translations.get(FALLBACK.toString());
            if (fallbackTranslations == null) {
                fallbackTranslations = translations.get(FALLBACK.getLanguage());
            }
            
            if (fallbackTranslations != null) {
                current = fallbackTranslations;

                for (String part : keyParts) {
                    if (current == null || !current.isJsonObject()) {
                        break;
                    }
                    current = current.getAsJsonObject().get(part);
                }

                if (current != null && current.isJsonPrimitive()) {
                    QuakePlugin.INSTANCE.getLogger().warning("TranslationManager could not get translation of " + key + " for locale " + localeString + ", falling back to " + FALLBACK.toString());
                    return current.getAsString();
                }
            }
        }

        QuakePlugin.INSTANCE.getLogger().severe("TranslationManager could not get translation of " + key);
        return key;
    }

    private String getOverride(String key) {
        if (QuakePlugin.INSTANCE.config == null || QuakePlugin.INSTANCE.config.locale == null || QuakePlugin.INSTANCE.config.locale.overrides == null) {
            return null;
        }

        String override = QuakePlugin.INSTANCE.config.locale.overrides.get(key);
        if (override == null || override.isBlank()) {
            return null;
        }

        return override;
    }

    public Component translate(String key, Locale locale, TagResolver... placeholders) {
        String translatedString = getTranslationString(key, locale);
        if (placeholders.length > 0) {
            return miniMessage.deserialize(translatedString, placeholders);
        }
        return miniMessage.deserialize(translatedString);
    }

    public Component translate(String key, Player player, TagResolver... placeholders) {
        return translate(key, player.locale(), placeholders);
    }

    public String translateLegacy(String key, Locale locale) {
        return getTranslationString(key, locale);
    }

    public String translateLegacy(String key, Player player) {
        return getTranslationString(key, player.locale());
    }

    public static Component t(String key, Locale locale, TagResolver... placeholders) {
        return INSTANCE.translate(key, locale, placeholders);
    }

    public static Component t(String key, Player player, TagResolver... placeholders) {
        return INSTANCE.translate(key, player, placeholders);
    }

    public static String tLegacy(String key, Locale locale) {
        return INSTANCE.translateLegacy(key, locale);
    }

    public static String tLegacy(String key, Player player) {
        return INSTANCE.translateLegacy(key, player);
    }
}
