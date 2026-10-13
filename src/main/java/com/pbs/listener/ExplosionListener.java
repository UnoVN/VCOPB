package com.pbs.listener;

import com.pbs.PlayerBlockShield;
import com.pbs.hook.WorldGuardHook;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.List;

public class ExplosionListener implements Listener {

    private final PlayerBlockShield plugin;

    public ExplosionListener(PlayerBlockShield plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    private void handleExplosion(List<Block> blocks) {
        boolean damagePlayerBlocks = plugin.getConfig().getBoolean("explosions.damage-player-blocks", true);

        blocks.removeIf(block -> {
            Location loc = block.getLocation();
            if (!WorldGuardHook.shouldProtect(plugin, loc, null)) {
                return false;
            }

            boolean isPlayerPlaced = plugin.getDataManager().isPlayerPlaced(loc);

            if (!isPlayerPlaced) {
                // Khối tự nhiên -> Giữ nguyên (loại khỏi danh sách nổ)
                return true;
            } else {
                // Khối do người chơi đặt -> Cho nổ và xóa khỏi data
                if (damagePlayerBlocks) {
                    plugin.getDataManager().removeBlock(loc);
                    return false;
                } else {
                    return true;
                }
            }
        });
    }
}
