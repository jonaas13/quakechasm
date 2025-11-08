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

// Paper-specific events removed for Spigot compatibility
// EntityAddToWorldEvent -> EntitySpawnEvent + ChunkLoadEvent
// EntityRemoveFromWorldEvent -> Removed (triggers loaded on plugin start)
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import com.github.polyzium.quakechasm.game.entities.QEntityUtil;
import com.github.polyzium.quakechasm.game.entities.Trigger;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import com.github.polyzium.quakechasm.QuakePlugin;
import com.github.polyzium.quakechasm.game.entities.*;
import com.github.polyzium.quakechasm.game.entities.pickups.Spawner;

import java.util.Set;

public class TriggerListener implements Listener {
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        for (int i = 0; i < QuakePlugin.INSTANCE.triggers.size(); i++) {
            Trigger trigger = QuakePlugin.INSTANCE.triggers.get(i);

            Location triggerLoc = trigger.getLocation();
            if (trigger.isDead()) {
                Bukkit.getLogger().warning(String.format("Removing dead trigger %s at %.1f %.1f %.1f", trigger.getClass().getSimpleName(), triggerLoc.getX(), triggerLoc.getY(), triggerLoc.getZ()));
                trigger.remove();
                QuakePlugin.INSTANCE.triggers.remove(trigger);
                continue;
            }

            Player player = event.getPlayer();
            World world = player.getLocation().getWorld();
            if (!world.getName().equals(trigger.getEntity().getWorld().getName())) return;
            BoundingBox bb = trigger.getOffsetBoundingBox();
            Vector triggerMin = triggerLoc.toVector().add(bb.getMin());
            Vector triggerMax = triggerLoc.toVector().add(bb.getMax());
            BoundingBox absoluteBb = BoundingBox.of(triggerMin, triggerMax);

            boolean holdingEntityTool = QuakePlugin.INSTANCE.userStates.get(player).holdingEntityTool;

            if (absoluteBb.overlaps(player.getBoundingBox()) && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR && !holdingEntityTool) {
                trigger.onTrigger(player);
            }
        }
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        if (QEntityUtil.getEntityType(entity) == null) return;

        QuakePlugin.INSTANCE.loadTrigger(entity);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // Load triggers from newly loaded chunks
        for (Entity entity : event.getChunk().getEntities()) {
            if (QEntityUtil.getEntityType(entity) == null) continue;
            
            // Check if trigger already exists
            boolean exists = QuakePlugin.INSTANCE.triggers.stream()
                .anyMatch(trigger -> trigger.getEntity().equals(entity));
            
            if (!exists) {
                QuakePlugin.INSTANCE.loadTrigger(entity);
            }
        }
    }

    // EntityRemoveFromWorldEvent removed for Spigot compatibility
    // Triggers are now cleaned up via the periodic dead trigger check in onPlayerMove
}
