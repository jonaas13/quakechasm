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

package com.github.polyzium.quakechasm.game.entities.pickups;

import com.github.polyzium.quakechasm.QuakePlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class PickupItemModels {
    private PickupItemModels() {
    }

    static void applyHealthModel(ItemStack item, int health) {
        switch (health) {
            case 1 -> applyItemModel(item, "health_small");
            case 5 -> applyItemModel(item, "health_medium");
            case 10 -> applyItemModel(item, "health_large");
            case 20 -> applyItemModel(item, "health_mega");
            default -> {
            }
        }
    }

    static void applyArmorModel(ItemStack item, int armor) {
        switch (armor) {
            case 5 -> applyItemModel(item, "armor_shard");
            case 50 -> applyItemModel(item, "armor_light");
            case 100 -> applyItemModel(item, "armor_heavy");
            default -> {
            }
        }
    }

    private static void applyItemModel(ItemStack item, String modelId) {
        if (item == null || item.isEmpty()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        meta.setItemModel(new NamespacedKey(QuakePlugin.INSTANCE, modelId));
        item.setItemMeta(meta);
    }
}
