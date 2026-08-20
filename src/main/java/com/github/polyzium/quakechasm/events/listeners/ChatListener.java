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

import com.github.polyzium.quakechasm.matchmaking.Team;
import com.github.polyzium.quakechasm.misc.TranslationManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import com.github.polyzium.quakechasm.QuakePlugin;
import com.github.polyzium.quakechasm.QuakeUserState;
import com.github.polyzium.quakechasm.misc.Chatroom;

public class ChatListener implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        QuakeUserState userState = QuakePlugin.INSTANCE.userStates.get(player);
        if (userState == null) return;

        String messageText = PlainTextComponentSerializer.plainText().serialize(event.message());

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
            player.sendMessage(TranslationManager.t("error.chat.switchNoMatch.title", player,
                    Placeholder.component("chatroom", TranslationManager.t("error.chat.switchNoMatch." + targetChatroom.name().toLowerCase() + "Adj", player).color(TextColor.color(targetChatroom.getColor())))
            ));
            event.setCancelled(true);
            return;
        }

        if (userState.currentMatch != null &&
                userState.currentMatch.allowedTeams().stream().allMatch(team -> team == Team.FREE) &&
                targetChatroom == Chatroom.TEAM) {
            player.sendMessage(TranslationManager.t("error.match.notTeam", player));
            event.setCancelled(true);
            return;
        }

        event.message(Component.text(processedMessage));
        if (targetChatroom == Chatroom.GLOBAL && userState.currentMatch != null) {
            sendGlobalOutsideMatches(event, player);
            event.setCancelled(true);
            return;
        }

        filterViewers(event, player, userState, targetChatroom);
    }

    private void sendGlobalOutsideMatches(AsyncChatEvent event, Player source) {
        Component renderedMessage = null;
        Component message = event.message();
        for (Audience viewer : event.viewers()) {
            if (!(viewer instanceof Player viewerPlayer)) {
                continue;
            }

            QuakeUserState viewerState = QuakePlugin.INSTANCE.userStates.get(viewerPlayer);
            if (viewerState != null && viewerState.currentMatch != null) {
                continue;
            }

            if (renderedMessage == null) {
                renderedMessage = event.renderer().render(source, source.displayName(), message, viewer);
            }
            viewerPlayer.sendMessage(renderedMessage);
        }
    }

    private void filterViewers(AsyncChatEvent event, Player source, QuakeUserState sourceState, Chatroom chatroom) {
        event.viewers().removeIf(viewer -> {
            if (!(viewer instanceof Player viewerPlayer)) {
                return chatroom != Chatroom.GLOBAL;
            }

            QuakeUserState viewerState = QuakePlugin.INSTANCE.userStates.get(viewerPlayer);
            return switch (chatroom) {
                case GLOBAL -> viewerState != null && viewerState.currentMatch != null;
                case MATCH -> viewerState == null || viewerState.currentMatch != sourceState.currentMatch;
                case TEAM -> viewerState == null
                        || viewerState.currentMatch != sourceState.currentMatch
                        || sourceState.currentMatch.getTeamOfPlayer(viewerPlayer) != sourceState.currentMatch.getTeamOfPlayer(source);
            };
        });
    }
}
