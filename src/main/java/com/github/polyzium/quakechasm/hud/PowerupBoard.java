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

package com.github.polyzium.quakechasm.hud;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.TextColor;
import com.github.polyzium.quakechasm.QuakeUserState;
import com.github.polyzium.quakechasm.game.combat.powerup.Powerup;
import com.github.polyzium.quakechasm.game.combat.powerup.PowerupType;

import java.util.ArrayList;
import java.util.EnumMap;

public class PowerupBoard {
    QuakeUserState state;
    private boolean needsUpdate = true;
    private Component cachedComponent = Component.empty();
    public static EnumMap<PowerupType, Character> ICONS = new EnumMap<>(PowerupType.class);
    static {
        ICONS.put(PowerupType.QUAD_DAMAGE, Icons.QUAD_DAMAGE);
        ICONS.put(PowerupType.REGENERATION, Icons.REGENERATION);
        ICONS.put(PowerupType.PROTECTION, Icons.PROTECTION);
    };

    public PowerupBoard(QuakeUserState state) {
        this.state = state;
    }

    public void rebuild() {
        this.update();
    }

    public void update() {
        this.needsUpdate = true;
    }

    public Component draw() {
        if (!needsUpdate) {
            return cachedComponent;
        }

        if (state.activePowerups.isEmpty()) {
            cachedComponent = Component.empty();
            needsUpdate = false;
            return cachedComponent;
        }

        ArrayList<Component> powerups = new ArrayList<>(state.activePowerups.size());
        for (int i = 0; i < state.activePowerups.size(); i++) {
            Powerup powerup = state.activePowerups.get(i);

            Component icon = Component.text(ICONS.get(powerup.getType())).font(Key.key("hud"));
            Component time = Component.text(powerup.getTime()).color(TextColor.color(0xff3f3f)).font(Key.key("hud"));
            Component component = Component.join(JoinConfiguration.noSeparators(), icon, time);

            powerups.add(component);
        }

        cachedComponent = Component.join(JoinConfiguration.separator(Component.text(" ")), powerups);
        this.needsUpdate = false;
        return cachedComponent;
    }
}
