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

package com.github.polyzium.quakechasm.events.listeners;

import com.github.polyzium.quakechasm.QuakePlugin;
import com.github.polyzium.quakechasm.QuakeUserState;
import com.github.polyzium.quakechasm.matchmaking.menu.MatchBrowserMenu;
import com.github.polyzium.quakechasm.matchmaking.matches.Match;
import com.github.polyzium.quakechasm.matchmaking.matches.MatchPrivacy;
import com.github.polyzium.quakechasm.misc.TranslationManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class MatchBrowserListener implements Listener {
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MatchBrowserMenu.Holder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }

        Match match = holder.getMatch(slot);
        if (match == null) {
            return;
        }

        QuakeUserState userState = QuakePlugin.INSTANCE.userStates.get(player);
        if (userState == null || userState.currentMatch != null) {
            player.sendMessage(TranslationManager.t("error.match.already", player));
            return;
        }

        if (!match.canJoin(player, null)) {
            if (match.getPrivacy() == MatchPrivacy.PASSWORD) {
                player.sendMessage(TranslationManager.t("match.privacy.incorrectPassword", player));
            } else if (match.getPrivacy() == MatchPrivacy.INVITE_ONLY) {
                player.sendMessage(TranslationManager.t("match.privacy.inviteRequired", player));
            } else {
                player.sendMessage(TranslationManager.t("match.privacy.cannotJoin", player));
            }
            return;
        }

        player.closeInventory();
        match.join(player, null);
    }
}
