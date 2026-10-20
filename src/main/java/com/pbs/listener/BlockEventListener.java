package com.pbs.listener;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import com.pbs.PlayerBlockShield;
import com.pbs.hook.WorldGuardHook;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public class BlockEventListener implements Listener {

    private final PlayerBlockShield plugin;

    public BlockEventListener(PlayerBlockShield plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        boolean isOpOrBypass = player.isOp() || player.hasPermission("pbs.bypass");
        boolean protectOpBlocks = plugin.getConfig().getBoolean("protect-op-placed-blocks", true);

        // Xử lý các khối 2 ô (Cửa, Giường, Cây cao 2 block...)
        if (event instanceof BlockMultiPlaceEvent multiPlaceEvent) {
            for (BlockState state : multiPlaceEvent.getReplacedBlockStates()) {
                Location loc = state.getLocation();
                if (WorldGuardHook.isShieldActiveAt(plugin, loc)) {
                    if (protectOpBlocks && isOpOrBypass) {
                        plugin.getDataManager().removeBlock(loc);
                    } else {
                        plugin.getDataManager().addBlock(loc);
                    }
                }
            }
            return;
        }

        Location loc = event.getBlock().getLocation();
        if (WorldGuardHook.isShieldActiveAt(plugin, loc)) {
            if (protectOpBlocks && isOpOrBypass) {
                // Nếu OP đặt block -> Xóa khỏi danh sách block người chơi để bảo vệ như block của Map
                plugin.getDataManager().removeBlock(loc);
            } else {
                plugin.getDataManager().addBlock(loc);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();
        Player player = event.getPlayer();

        if (!WorldGuardHook.isShieldActiveAt(plugin, loc)) {
            return;
        }

        if (player.hasPermission("pbs.bypass")) {
            event.setCancelled(false);
            removeBlockAndMultiHalf(block);
            return;
        }

        boolean isPlayerPlaced = plugin.getDataManager().isPlayerPlaced(loc);

        if (isPlayerPlaced) {
            event.setCancelled(false);
            removeBlockAndMultiHalf(block);
        } else {
            event.setCancelled(true);
            String msg = plugin.getConfig().getString("messages.cannot-break", "&cKhong the pha block tu nhien!");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        }
    }

    // Bảo vệ các block của Map (Ray, Thảm, Nút, Cửa, Đuốc, Hoa, Cỏ...) khỏi bị rớt do hiệu ứng vật lý
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDestroy(BlockDestroyEvent event) {
        Location loc = event.getBlock().getLocation();
        if (!WorldGuardHook.isShieldActiveAt(plugin, loc)) {
            return;
        }

        if (plugin.getDataManager().isPlayerPlaced(loc)) {
            removeBlockAndMultiHalf(event.getBlock());
        } else {
            event.setCancelled(true);
        }
    }

    // Bảo vệ khối TNT của Map/OP khỏi bị kích nổ bởi vụ nổ TNT Cart, tên lửa, đá đỏ...
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTNTPrime(TNTPrimeEvent event) {
        Location loc = event.getBlock().getLocation();
        if (!WorldGuardHook.isShieldActiveAt(plugin, loc)) {
            return;
        }

        if (plugin.getDataManager().isPlayerPlaced(loc)) {
            plugin.getDataManager().removeBlock(loc);
        } else {
            if (event.getPrimingEntity() instanceof Player player && player.hasPermission("pbs.bypass")) {
                return;
            }
            event.setCancelled(true);
        }
    }

    // Bảo vệ Cát, Sỏi, Đe, Bê tông bột của Map khỏi bị rơi khi TNT Cart nổ cạnh, hoặc bị Entity phá
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Location loc = event.getBlock().getLocation();
        if (!WorldGuardHook.isShieldActiveAt(plugin, loc)) {
            return;
        }

        if (plugin.getDataManager().isPlayerPlaced(loc)) {
            if (event.getTo().isAir()) {
                plugin.getDataManager().removeBlock(loc);
            }
        } else {
            event.setCancelled(true);
        }
    }

    // Ngăn nước/lava chảy làm trôi đường ray, thảm, nút bấm, hoa, cỏ của Map gốc
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        Block toBlock = event.getToBlock();
        if (toBlock.getType().isAir() || toBlock.isLiquid()) {
            return;
        }
        Location toLoc = toBlock.getLocation();
        if (WorldGuardHook.isShieldActiveAt(plugin, toLoc)
                && !plugin.getDataManager().isPlayerPlaced(toLoc)) {
            event.setCancelled(true);
        }
    }

    // Chặn lửa đốt cháy các khối tự nhiên của Map
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        Location loc = event.getBlock().getLocation();
        if (!WorldGuardHook.isShieldActiveAt(plugin, loc)) {
            return;
        }

        if (plugin.getDataManager().isPlayerPlaced(loc)) {
            plugin.getDataManager().removeBlock(loc);
        } else {
            event.setCancelled(true);
        }
    }

    // Chặn băng tan, san hô chết, đất ruộng bị biến đổi nếu là khối của Map
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        Location loc = event.getBlock().getLocation();
        if (!WorldGuardHook.isShieldActiveAt(plugin, loc)) {
            return;
        }

        if (plugin.getDataManager().isPlayerPlaced(loc)) {
            plugin.getDataManager().removeBlock(loc);
        } else {
            event.setCancelled(true);
        }
    }

    // Chặn lá cây tự nhiên của Map bị rụng
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        Location loc = event.getBlock().getLocation();
        if (!WorldGuardHook.isShieldActiveAt(plugin, loc)) {
            return;
        }

        if (plugin.getDataManager().isPlayerPlaced(loc)) {
            plugin.getDataManager().removeBlock(loc);
        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (WorldGuardHook.isWorldEnabled(plugin, event.getWorld())) {
            plugin.getDataManager().loadChunk(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (WorldGuardHook.isWorldEnabled(plugin, event.getWorld())) {
            plugin.getDataManager().unloadChunk(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
        }
    }

    public void removeBlockAndMultiHalf(Block block) {
        Location loc = block.getLocation();
        plugin.getDataManager().removeBlock(loc);

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
