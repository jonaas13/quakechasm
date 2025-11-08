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

package com.github.polyzium.quakechasm.misc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Locale;

public enum Chatroom {
    GLOBAL(0x55ff55),
    MATCH(0xffaa00),
    TEAM(0x55ffff);

    final int color;

    Chatroom(int color) {
        this.color = color;
    }

    public int getColor() { return this.color; }

    public Component getPrefix(Locale locale) {
        return Component.text(TranslationManager.tLegacy("command.chat.prefix."+this.name().toLowerCase(), locale))
                .color(TextColor.color(this.color))
                .decorate(TextDecoration.BOLD);
    }

    public Component getPrefix() {
        return getPrefix(TranslationManager.FALLBACK);
    }
}
