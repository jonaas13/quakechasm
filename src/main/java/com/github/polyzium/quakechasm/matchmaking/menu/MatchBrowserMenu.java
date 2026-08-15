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

import com.github.polyzium.quakechasm.QuakePlugin;
import com.github.polyzium.quakechasm.matchmaking.matches.Match;
import com.github.polyzium.quakechasm.matchmaking.matches.MatchPrivacy;
import com.github.polyzium.quakechasm.misc.TranslationManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MatchBrowserMenu {
    public static void open(Player player) {
        List<Match> matches = QuakePlugin.INSTANCE.matchManager.getVisibleMatches(player);
        int size = Math.max(9, Math.min(54, ((matches.size() + 8) / 9) * 9));

        Holder holder = new Holder(matches);
        Inventory inventory = Bukkit.createInventory(holder, size, "Quakechasm Matches");
        holder.setInventory(inventory);

        Locale locale = player.locale();
        for (int i = 0; i < Math.min(matches.size(), size); i++) {
            inventory.setItem(i, createMatchItem(matches.get(i), i, locale));
        }

        player.openInventory(inventory);
    }

    private static ItemStack createMatchItem(Match match, int index, Locale locale) {
        ItemStack item = new ItemStack(materialForPrivacy(match.getPrivacy()));
        ItemMeta meta = item.getItemMeta();

        String matchType = TranslationManager.tLegacy(match.getNameKey(), locale);
        meta.displayName(Component.text(matchType + " - " + match.getMap().displayName)
                .color(TextColor.color(0xffffff)));

        ArrayList<Component> lore = new ArrayList<>();
        lore.add(Component.text("Index: " + index).color(TextColor.color(0x909090)));
        lore.add(Component.text("Map: " + match.getMap().name).color(TextColor.color(0x909090)));
        lore.add(Component.text("Players: " + match.getPlayers().size()).color(TextColor.color(0x909090)));
        lore.add(Component.text("Privacy: " + match.getPrivacy().name()).color(TextColor.color(0x909090)));
        lore.add(Component.empty());
        lore.add(Component.text("Click to join").color(TextColor.color(0x55ff55)));
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private static Material materialForPrivacy(MatchPrivacy privacy) {
        return switch (privacy) {
            case PUBLIC -> Material.LIME_CONCRETE;
            case PASSWORD -> Material.YELLOW_CONCRETE;
            case INVITE_ONLY -> Material.RED_CONCRETE;
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
