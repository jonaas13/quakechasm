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

package com.github.polyzium.quakechasm.matchmaking.matches;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bukkit.entity.Player;
import com.github.polyzium.quakechasm.QuakePlugin;
import com.github.polyzium.quakechasm.matchmaking.factory.MatchFactory;
import com.github.polyzium.quakechasm.matchmaking.map.QMap;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class MatchManager {
    public static MatchManager INSTANCE;
    public ArrayList<Match> matches;

    public MatchManager() {
        this.matches = new ArrayList<>(10);

        // singleton
        INSTANCE = this;
    }

    public Match newMatch(MatchFactory matchFactory, QMap map) {
        return newMatch(matchFactory, map, null, MatchPrivacy.PUBLIC, null);
    }
    
    public Match newMatch(MatchFactory matchFactory, QMap map, UUID ownerId,
                         MatchPrivacy privacy, String password) {
        Match match = null;
        try {
            match = matchFactory.createMatch(map, ownerId, privacy, password);
        } catch (Exception e) {
            QuakePlugin.INSTANCE.getLogger().severe(
                    "Caught an exception from "+matchFactory.getClass().getSimpleName()+":\n"+
                            ExceptionUtils.getStackTrace(e)
            );
        }
        if (match == null) {
            QuakePlugin.INSTANCE.getLogger().severe("Failed to make a "+matchFactory.getNameKey()+" match");
            return null;
        }

        matches.add(match);
        return match;
    }

    public List<Match> getVisibleMatches(Player player) {
        boolean isAdmin = player.hasPermission("quake.admin");
        
        return matches.stream()
            .filter(match -> {
                if (isAdmin) return true;

                if (match.getPrivacy() == MatchPrivacy.INVITE_ONLY) {
                    return match.isOwner(player) || match.isInvited(player.getUniqueId());
                }

                return true;
            })
            .collect(Collectors.toList());
    }

    public Match getVisibleMatch(Player player, int index) {
        List<Match> visible = getVisibleMatches(player);
        if (index < 0 || index >= visible.size()) return null;
        return visible.get(index);
    }

    public Match getMatchByOwner(UUID ownerId) {
        return matches.stream()
            .filter(match -> match.getOwnerId() != null &&
                            match.getOwnerId().equals(ownerId))
            .findFirst()
            .orElse(null);
    }
}
