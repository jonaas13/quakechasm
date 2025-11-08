/*
 * Quakechasm, a Quake minigame plugin for Minecraft servers running Spigot
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

import com.github.polyzium.quakechasm.matchmaking.Team;
import com.github.polyzium.quakechasm.misc.TranslationManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import com.github.polyzium.quakechasm.QuakePlugin;
import com.github.polyzium.quakechasm.QuakeUserState;
import com.github.polyzium.quakechasm.misc.Chatroom;

public class ChatListener implements Listener {
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        QuakeUserState userState = QuakePlugin.INSTANCE.userStates.get(player);

        String messageText = event.getMessage();

        Chatroom targetChatroom = userState.currentChat;
        String processedMessage = messageText;

        if (!messageText.isEmpty()) {
            char firstChar = messageText.charAt(0);

            if (messageText.length() > 1 && messageText.charAt(1) == firstChar) {
                if (firstChar == '@' || firstChar == '#' || firstChar == '$') {
                    processedMessage = messageText.substring(1);
                }
            }
            else if (firstChar == '@') {
                targetChatroom = Chatroom.TEAM;
                processedMessage = messageText.substring(1);
            }
            else if (firstChar == '#') {
                targetChatroom = Chatroom.MATCH;
                processedMessage = messageText.substring(1);
            }
            else if (firstChar == '$') {
                targetChatroom = Chatroom.GLOBAL;
                processedMessage = messageText.substring(1);
            }
        }

        if (userState.currentMatch == null && (targetChatroom == Chatroom.MATCH || targetChatroom == Chatroom.TEAM)) {
            QuakePlugin.INSTANCE.adventure().player(player).sendMessage(TranslationManager.t("error.chat.switchNoMatch.title", player,
                    Placeholder.component("chatroom", TranslationManager.t("error.chat.switchNoMatch." + targetChatroom.name().toLowerCase() + "Adj", player).color(TextColor.color(targetChatroom.getColor())))
            ));
            event.setCancelled(true);
            return;
        }

        if (userState.currentMatch != null &&
                userState.currentMatch.allowedTeams().stream().allMatch(team -> team == Team.FREE) &&
                targetChatroom == Chatroom.TEAM) {
            QuakePlugin.INSTANCE.adventure().player(player).sendMessage(TranslationManager.t("error.match.notTeam", player));
            event.setCancelled(true);
            return;
        }

        event.setMessage(processedMessage);

        switch (targetChatroom) {
            case GLOBAL -> chatGlobal(event);
            case MATCH -> chatMatch(event);
            case TEAM -> chatTeam(event);
        }
    }

    public void chatGlobal(AsyncPlayerChatEvent event) {
        Component prefix = Chatroom.GLOBAL.getPrefix(TranslationManager.getPlayerLocale(event.getPlayer()));
        Component formatted = Component.textOfChildren(
            prefix,
            MiniMessage.miniMessage().deserialize(
                " <b><color:#7f7f7f><player></color></b> <message>",
                Placeholder.unparsed("player", event.getPlayer().getName()),
                Placeholder.unparsed("message", event.getMessage())
            )
        );
        event.setFormat(LegacyComponentSerializer.legacySection().serialize(formatted));
    }

    public void chatMatch(AsyncPlayerChatEvent event) {
        QuakeUserState userState = QuakePlugin.INSTANCE.userStates.get(event.getPlayer());

        event.getRecipients().clear();
        event.getRecipients().addAll(userState.currentMatch.getPlayers());

        Component prefix = Chatroom.MATCH.getPrefix(TranslationManager.getPlayerLocale(event.getPlayer()));
        Component formatted = Component.textOfChildren(
            prefix,
            MiniMessage.miniMessage().deserialize(
                " <b><color:#7f7f7f><player></color></b> <message>",
                Placeholder.unparsed("player", event.getPlayer().getName()),
                Placeholder.unparsed("message", event.getMessage())
            )
        );
        event.setFormat(LegacyComponentSerializer.legacySection().serialize(formatted));
    }

    public void chatTeam(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        QuakeUserState userState = QuakePlugin.INSTANCE.userStates.get(player);

        event.getRecipients().clear();
        event.getRecipients().addAll(userState.currentMatch.getPlayersInTeam(
                userState.currentMatch.getTeamOfPlayer(player)
        ));

        Component prefix = Chatroom.TEAM.getPrefix(TranslationManager.getPlayerLocale(player));
        Component formatted = Component.textOfChildren(
            prefix,
            MiniMessage.miniMessage().deserialize(
                " <b><color:#7f7f7f><player></color></b> <message>",
                Placeholder.unparsed("player", player.getName()),
                Placeholder.unparsed("message", event.getMessage())
            )
        );
        event.setFormat(LegacyComponentSerializer.legacySection().serialize(formatted));
    }
}
