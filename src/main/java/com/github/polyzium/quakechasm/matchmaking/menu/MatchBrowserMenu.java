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

package com.github.polyzium.quakechasm.matchmaking.menu;

import com.github.polyzium.quakechasm.PluginConfig;
import com.github.polyzium.quakechasm.QuakePlugin;
import com.github.polyzium.quakechasm.matchmaking.setup.GameSetup;
import com.github.polyzium.quakechasm.matchmaking.matches.Match;
import com.github.polyzium.quakechasm.matchmaking.matches.MatchPrivacy;
import com.github.polyzium.quakechasm.misc.TranslationManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MatchBrowserMenu {
    public static void open(Player player) {
        List<Match> matches = QuakePlugin.INSTANCE.matchManager.getVisibleMatches(player);
        PluginConfig.MatchBrowserGuiConfig config = QuakePlugin.INSTANCE.config.gui.matchBrowser;
        Locale locale = player.locale();
        int rows = Math.max(clampRows(config.minRows), Math.min(clampRows(config.maxRows), Math.max(1, (matches.size() + 8) / 9)));
        int size = rows * 9;

        Holder holder = new Holder(matches);
        Inventory inventory = Bukkit.createInventory(holder, size, TranslationManager.t("menu.matchBrowser.title", locale));
        holder.setInventory(inventory);

        if (config.fillEmptySlots) {
            ItemStack filler = createFillerItem(config);
            for (int i = 0; i < size; i++) {
                inventory.setItem(i, filler);
            }
        }

        if (matches.isEmpty()) {
            inventory.setItem(size / 2, createEmptyItem(config, locale));
            player.openInventory(inventory);
            return;
        }

        for (int i = 0; i < Math.min(matches.size(), size); i++) {
            inventory.setItem(i, createMatchItem(matches.get(i), i, config, locale));
        }

        player.openInventory(inventory);
    }

    private static ItemStack createMatchItem(Match match, int index, PluginConfig.MatchBrowserGuiConfig config, Locale locale) {
        GameSetup setup = QuakePlugin.INSTANCE.matchmakingService.getSetupForMap(match.getMap());
        ItemStack item = new ItemStack(materialForPrivacy(match.getPrivacy(), config));
        ItemMeta meta = item.getItemMeta();

        Component matchType = TranslationManager.t(match.getNameKey(), locale);
        meta.displayName(itemText(TranslationManager.t("menu.matchBrowser.match.name", locale,
                Placeholder.component("match_type", matchType),
                Placeholder.unparsed("map_name", match.getMap().getDisplayName())), config));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        ArrayList<Component> lore = new ArrayList<>();
        lore.add(TranslationManager.t("menu.matchBrowser.match.lore.status", locale,
                Placeholder.unparsed("index", String.valueOf(index)),
                Placeholder.component("status", statusComponent(match, locale))));
        lore.add(TranslationManager.t("menu.matchBrowser.match.lore.mode", locale,
                Placeholder.component("match_type", matchType)));
        lore.add(TranslationManager.t("menu.matchBrowser.match.lore.map", locale,
                Placeholder.unparsed("map_name", match.getMap().getDisplayName())));
        lore.add(TranslationManager.t("menu.matchBrowser.match.lore.players", locale,
                Placeholder.unparsed("players", String.valueOf(match.getPlayers().size())),
                Placeholder.unparsed("max_players", maxPlayersText(setup, locale))));
        if (config.showScoreLimit) {
            lore.add(TranslationManager.t("menu.matchBrowser.match.lore.scoreLimit", locale,
                    Placeholder.unparsed("score_limit", String.valueOf(setup.getScoreLimit()))));
        }
        lore.add(TranslationManager.t("menu.matchBrowser.match.lore.timeLimit", locale,
                Placeholder.unparsed("time_limit", Match.formatTime(setup.getTimeLimitSeconds()))));
        lore.add(TranslationManager.t("menu.matchBrowser.match.lore.privacy", locale,
                Placeholder.component("privacy", privacyComponent(match.getPrivacy(), locale))));
        if (config.showOwner) {
            lore.add(ownerComponent(match, locale));
        }
        lore.add(Component.empty());
        lore.add(actionComponent(match.getPrivacy(), locale));
        meta.lore(itemLore(lore, config));

        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createEmptyItem(PluginConfig.MatchBrowserGuiConfig config, Locale locale) {
        ItemStack item = new ItemStack(configuredMaterial(config.emptyMaterial, Material.BARRIER));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(itemText(TranslationManager.t("menu.matchBrowser.empty.name", locale), config));
        meta.lore(itemLore(List.of(
                TranslationManager.t("menu.matchBrowser.empty.lore", locale)
        ), config));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createFillerItem(PluginConfig.MatchBrowserGuiConfig config) {
        ItemStack item = new ItemStack(configuredMaterial(config.fillerMaterial, Material.GRAY_STAINED_GLASS_PANE));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(itemText(Component.empty(), config));
        meta.setHideTooltip(true);
        item.setItemMeta(meta);
        return item;
    }

    private static Component itemText(Component component, PluginConfig.MatchBrowserGuiConfig config) {
        if (!config.forcePlainText) {
            return component;
        }

        return component.decoration(TextDecoration.ITALIC, false);
    }

    private static List<Component> itemLore(List<Component> lore, PluginConfig.MatchBrowserGuiConfig config) {
        if (!config.forcePlainText) {
            return lore;
        }

        return lore.stream()
                .map(component -> component.decoration(TextDecoration.ITALIC, false))
                .toList();
    }

    private static Material materialForPrivacy(MatchPrivacy privacy, PluginConfig.MatchBrowserGuiConfig config) {
        return switch (privacy) {
            case PUBLIC -> configuredMaterial(config.publicMaterial, Material.LIME_CONCRETE);
            case PASSWORD -> configuredMaterial(config.passwordMaterial, Material.YELLOW_CONCRETE);
            case INVITE_ONLY -> configuredMaterial(config.inviteOnlyMaterial, Material.RED_CONCRETE);
        };
    }

    private static Material configuredMaterial(String materialName, Material fallback) {
        Material material = materialName == null ? null : Material.matchMaterial(materialName);
        if (material == null || !material.isItem() || material == Material.AIR) {
            return fallback;
        }

        return material;
    }

    private static int clampRows(int rows) {
        return Math.max(1, Math.min(6, rows));
    }

    private static String maxPlayersText(GameSetup setup, Locale locale) {
        if (setup.hasPlayerLimit()) {
            return String.valueOf(setup.getMaxPlayers());
        }

        return TranslationManager.tLegacy("menu.matchBrowser.match.unlimitedPlayers", locale);
    }

    private static Component statusComponent(Match match, Locale locale) {
        if (match.matchEnding) {
            return TranslationManager.t("menu.matchBrowser.match.status.ending", locale);
        }

        return TranslationManager.t("menu.matchBrowser.match.status.open", locale);
    }

    private static Component privacyComponent(MatchPrivacy privacy, Locale locale) {
        return switch (privacy) {
            case PUBLIC -> TranslationManager.t("menu.matchBrowser.match.privacy.public", locale);
            case PASSWORD -> TranslationManager.t("menu.matchBrowser.match.privacy.password", locale);
            case INVITE_ONLY -> TranslationManager.t("menu.matchBrowser.match.privacy.inviteOnly", locale);
        };
    }

    private static Component ownerComponent(Match match, Locale locale) {
        if (match.getOwnerId() == null) {
            return TranslationManager.t("menu.matchBrowser.match.owner.matchmaking", locale);
        }

        OfflinePlayer owner = Bukkit.getOfflinePlayer(match.getOwnerId());
        String ownerName = owner.getName() != null ? owner.getName() : match.getOwnerId().toString();
        return TranslationManager.t("menu.matchBrowser.match.owner.player", locale,
                Placeholder.unparsed("owner_name", ownerName));
    }

    private static Component actionComponent(MatchPrivacy privacy, Locale locale) {
        return switch (privacy) {
            case PUBLIC -> TranslationManager.t("menu.matchBrowser.match.action.join", locale);
            case PASSWORD -> TranslationManager.t("menu.matchBrowser.match.action.password", locale);
            case INVITE_ONLY -> TranslationManager.t("menu.matchBrowser.match.action.inviteOnly", locale);
        };
    }

    public static class Holder implements InventoryHolder {
        private final List<Match> matches;
        private Inventory inventory;

        private Holder(List<Match> matches) {
            this.matches = matches;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public Match getMatch(int slot) {
            if (slot < 0 || slot >= matches.size()) {
                return null;
            }
            return matches.get(slot);
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
