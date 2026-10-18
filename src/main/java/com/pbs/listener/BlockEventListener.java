package com.pbs.listener;

import com.pbs.PlayerBlockShield;
import com.pbs.hook.WorldGuardHook;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public class BlockEventListener implements Listener {

    private final PlayerBlockShield plugin;

    public BlockEventListener(PlayerBlockShield plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Location loc = event.getBlock().getLocation();
        
        // Chỉ cần vị trí nằm trong khu vực áp dụng Shield -> Lưu lại ngay lập tức
        // Bất kể người đặt có OP hay không có OP
        if (WorldGuardHook.isShieldActiveAt(plugin, loc)) {
            plugin.getDataManager().addBlock(loc);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();
        Player player = event.getPlayer();

        // 1. Nếu vị trí không nằm trong phạm vi quản lý -> Bỏ qua cho WorldGuard tự xử
        if (!WorldGuardHook.isShieldActiveAt(plugin, loc)) {
            return;
        }

        // 2. Nếu người chơi có quyền Bypass (Admin/Staff) -> Cho phép phá tất cả
        if (player.hasPermission("pbs.bypass")) {
            event.setCancelled(false);
            plugin.getDataManager().removeBlock(loc);
            return;
        }

        // 3. Kiểm tra xem khối này có phải do người chơi đặt không
        boolean isPlayerPlaced = plugin.getDataManager().isPlayerPlaced(loc);

        if (isPlayerPlaced) {
            // Đúng khối người chơi đặt:
            // Gỡ bỏ lệnh cancel của WorldGuard (nếu có) và cho phép đập
            event.setCancelled(false);
            plugin.getDataManager().removeBlock(loc);
        } else {
            // Khối tự nhiên của Map: Hủy hành vi đập
            event.setCancelled(true);
            String msg = plugin.getConfig().getString("messages.cannot-break", "&cKhong the pha block tu nhien!");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        plugin.getDataManager().removeBlock(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        plugin.getDataManager().removeBlock(event.getBlock().getLocation());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (plugin.getConfig().getStringList("enabled-worlds").contains(event.getWorld().getName())) {
            plugin.getDataManager().loadChunk(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        plugin.getDataManager().unloadChunk(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
    }
}
