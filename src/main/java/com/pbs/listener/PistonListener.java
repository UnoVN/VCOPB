package com.pbs.listener;

import com.pbs.PlayerBlockShield;
import com.pbs.hook.WorldGuardHook;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

public class PistonListener implements Listener {

    private final PlayerBlockShield plugin;

    public PistonListener(PlayerBlockShield plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExtend(BlockPistonExtendEvent event) {
        if (!plugin.getConfig().getBoolean("pistons.prevent-moving-player-blocks", true)) return;

        for (Block block : event.getBlocks()) {
            if (WorldGuardHook.isShieldActiveAt(plugin, block.getLocation())
                    && plugin.getDataManager().isPlayerPlaced(block.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRetract(BlockPistonRetractEvent event) {
        if (!plugin.getConfig().getBoolean("pistons.prevent-moving-player-blocks", true)) return;

        for (Block block : event.getBlocks()) {
            if (WorldGuardHook.isShieldActiveAt(plugin, block.getLocation())
                    && plugin.getDataManager().isPlayerPlaced(block.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
