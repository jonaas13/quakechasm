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

package com.github.polyzium.quakechasm.game.combat;

import com.github.polyzium.quakechasm.hud.Icons;

public enum MedalType {
    EXCELLENT("game.medal.name.excellent", Icons.MEDAL_EXCELLENT),
    IMPRESSIVE("game.medal.name.impressive", Icons.MEDAL_IMPRESSIVE),
    ACCURACY("game.medal.name.accuracy", Icons.MEDAL_ACCURACY),
    HUMILIATION("game.medal.name.humiliation", Icons.MEDAL_HUMILIATION);

    private final String translationKey;
    private final char icon;

    MedalType(String translationKey, char icon) {
        this.translationKey = translationKey;
        this.icon = icon;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public char getIcon() {
        return icon;
    }
}