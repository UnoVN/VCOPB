package com.pbs.listener;

import com.pbs.PlayerBlockShield;
import com.pbs.hook.WorldGuardHook;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
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
    private final Map<Event, List<Block>> allowedExplosionBlocks = new IdentityHashMap<>();

    public ExplosionListener(PlayerBlockShield plugin) {
        this.plugin = plugin;
    }

    // GIAI ĐOẠN 1 (LOWEST): Chạy TRƯỚC WorldGuard để lọc sạch block tự nhiên/OP và lưu lại các block người chơi đặt
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEntityExplodeLowest(EntityExplodeEvent event) {
        captureAtLowest(event, event.getLocation(), event.blockList());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBlockExplodeLowest(BlockExplodeEvent event) {
        captureAtLowest(event, event.getBlock().getLocation(), event.blockList());
    }

    private void captureAtLowest(Event event, Location center, List<Block> blocks) {
        if (center == null || !WorldGuardHook.isWorldEnabled(plugin, center.getWorld())) {
            return;
        }

        boolean damagePlayerBlocks = plugin.getConfig().getBoolean("explosions.damage-player-blocks", true);
        boolean protectNaturalBlocks = plugin.getConfig().getBoolean("explosions.protect-natural-blocks", true);
        List<Block> allowed = new ArrayList<>();

        Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            Location loc = block.getLocation();

            // Nếu block nằm ở vùng không bật shield (ví dụ region set break-only-player-blocks: deny) -> Bảo vệ tuyệt đối
            if (!WorldGuardHook.isShieldActiveAt(plugin, loc)) {
                iterator.remove();
                continue;
            }

            if (plugin.getDataManager().isPlayerPlaced(loc)) {
                if (damagePlayerBlocks) {
                    allowed.add(block);
                } else {
                    iterator.remove();
                }
            } else {
                if (protectNaturalBlocks) {
                    iterator.remove();
                } else {
                    allowed.add(block);
                }
            }
        }

        allowedExplosionBlocks.put(event, allowed);
    }

    // GIAI ĐOẠN 2 (HIGHEST): Chạy SAU WorldGuard để gỡ cancel và ép danh sách nổ CHỈ chứa các block hợp lệ
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

        // Gỡ cancel của WorldGuard để vụ nổ vẫn diễn ra
        cancellable.setCancelled(false);

        // Xóa trắng toàn bộ blockList() hiện tại để đảm bảo KHÔNG MỘT BLOCK TỰ NHIÊN NÀO có thể lọt qua
        currentBlocks.clear();

        boolean protectNaturalBlocks = plugin.getConfig().getBoolean("explosions.protect-natural-blocks", true);

        for (Block block : allowed) {
            Location loc = block.getLocation();
            if (!WorldGuardHook.isShieldActiveAt(plugin, loc)) {
                continue;
            }
            if (protectNaturalBlocks && !plugin.getDataManager().isPlayerPlaced(loc)) {
                continue;
            }

            currentBlocks.add(block);
            removeBlockAndMultiHalf(block);
        }
    }

    private void removeBlockAndMultiHalf(Block block) {
        plugin.getDataManager().removeBlock(block.getLocation());

        BlockData data = block.getBlockData();
        if (data instanceof Bed bed) {
            BlockFace face = (bed.getPart() == Bed.Part.FOOT) ? bed.getFacing() : bed.getFacing().getOppositeFace();
            plugin.getDataManager().removeBlock(block.getRelative(face).getLocation());
        } else if (data instanceof Bisected bisected && !(data instanceof org.bukkit.block.data.type.Stairs)
                && !(data instanceof org.bukkit.block.data.type.TrapDoor)) {
            Block other = (bisected.getHalf() == Bisected.Half.BOTTOM)
                    ? block.getRelative(BlockFace.UP)
                    : block.getRelative(BlockFace.DOWN);
            if (other.getType() == block.getType()) {
                plugin.getDataManager().removeBlock(other.getLocation());
            }
        }
    }
}
