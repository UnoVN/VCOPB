package com.pbs.listener;

import com.pbs.PlayerBlockShield;
import com.pbs.hook.WorldGuardHook;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

import java.util.List;

public class PistonListener implements Listener {

    private final PlayerBlockShield plugin;

    public PistonListener(PlayerBlockShield plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExtend(BlockPistonExtendEvent event) {
        if (shouldCancelPiston(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRetract(BlockPistonRetractEvent event) {
        if (shouldCancelPiston(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    private boolean shouldCancelPiston(List<Block> blocks) {
        boolean preventMovingPlayerBlocks = plugin.getConfig().getBoolean("pistons.prevent-moving-player-blocks", true);

        for (Block block : blocks) {
            Location loc = block.getLocation();
            if (!WorldGuardHook.isShieldActiveAt(plugin, loc)) {
                continue;
            }

            boolean isPlayerPlaced = plugin.getDataManager().isPlayerPlaced(loc);

            // Luôn chặn Piston đẩy/kéo block tự nhiên của Map gốc (hoặc của OP)
            if (!isPlayerPlaced) {
                return true;
            }

            // Chặn Piston đẩy/kéo block người chơi nếu bật trong config
            if (preventMovingPlayerBlocks) {
                return true;
            }
        }
        return false;
    }
}
