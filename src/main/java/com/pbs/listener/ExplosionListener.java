package com.pbs.listener;

import com.pbs.PlayerBlockShield;
import com.pbs.hook.WorldGuardHook;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ExplosionListener implements Listener {

    private final PlayerBlockShield plugin;

    // Lưu tạm danh sách block người chơi đặt trước khi WorldGuard can thiệp
    private final Map<Event, List<Block>> allowedExplosionBlocks = new IdentityHashMap<>();

    public ExplosionListener(PlayerBlockShield plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    // GIAI ĐOẠN 1 (LOWEST): Chạy TRƯỚC WorldGuard để lưu lại các block người đặt
    // =========================================================================

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEntityExplodeLowest(EntityExplodeEvent event) {
        captureAtLowest(event, event.getLocation(), event.blockList());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBlockExplodeLowest(BlockExplodeEvent event) {
        captureAtLowest(event, event.getBlock().getLocation(), event.blockList());
    }

    private void captureAtLowest(Event event, Location center, List<Block> blocks) {
        boolean damagePlayerBlocks = plugin.getConfig().getBoolean("explosions.damage-player-blocks", true);
        boolean anyShielded = WorldGuardHook.isShieldActiveAt(plugin, center);
        List<Block> allowed = new ArrayList<>();

        Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            Location loc = block.getLocation();

            if (!WorldGuardHook.isShieldActiveAt(plugin, loc)) {
                continue;
            }

            anyShielded = true;
            if (plugin.getDataManager().isPlayerPlaced(loc)) {
                if (damagePlayerBlocks) {
                    allowed.add(block);
                } else {
                    iterator.remove();
                }
            } else {
                // Khối tự nhiên của Map -> Loại khỏi danh sách nổ ngay lập tức
                iterator.remove();
            }
        }

        if (anyShielded) {
            allowedExplosionBlocks.put(event, allowed);
        }
    }

    // =========================================================================
    // GIAI ĐOẠN 2 (HIGHEST): Chạy SAU WorldGuard để gỡ cancel và khôi phục block
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityExplodeHighest(EntityExplodeEvent event) {
        restoreAtHighest(event, event, event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockExplodeHighest(BlockExplodeEvent event) {
        restoreAtHighest(event, event, event.blockList());
    }

    private void restoreAtHighest(Event key, Cancellable cancellable, List<Block> currentBlocks) {
        List<Block> allowed = allowedExplosionBlocks.remove(key);
        if (allowed == null) {
            return;
        }

        // Gỡ lệnh hủy sự kiện nổ của WorldGuard để TNT Cart luôn nổ bình thường
        cancellable.setCancelled(false);

        // Đảm bảo không có khối tự nhiên nào trong vùng bảo vệ bị lọt vào
        currentBlocks.removeIf(block ->
                WorldGuardHook.isShieldActiveAt(plugin, block.getLocation())
                        && !plugin.getDataManager().isPlayerPlaced(block.getLocation())
        );

        // Khôi phục các khối do người chơi đặt (nút bấm, block...) đã bị WorldGuard xóa
        for (Block block : allowed) {
            if (!currentBlocks.contains(block)) {
                currentBlocks.add(block);
            }
            plugin.getDataManager().removeBlock(block.getLocation());
        }
    }
}
