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
        if (WorldGuardHook.shouldProtect(plugin, loc, event.getPlayer())) {
            plugin.getDataManager().addBlock(loc);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();
        Player player = event.getPlayer();

        if (!WorldGuardHook.shouldProtect(plugin, loc, player)) {
            return;
        }

        if (!plugin.getDataManager().isPlayerPlaced(loc)) {
            event.setCancelled(true);
            String msg = plugin.getConfig().getString("messages.cannot-break", "&cKhong the pha block nay!");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return;
        }

        plugin.getDataManager().removeBlock(loc);
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
