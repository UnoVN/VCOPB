package com.pbs;

import com.pbs.hook.WorldGuardHook;
import com.pbs.listener.BlockEventListener;
import com.pbs.listener.ExplosionListener;
import com.pbs.listener.PistonListener;
import com.pbs.manager.BlockDataManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayerBlockShield extends JavaPlugin implements CommandExecutor {

    private BlockDataManager dataManager;

    @Override
    public void onLoad() {
        WorldGuardHook.registerFlag();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.dataManager = new BlockDataManager(this);

        // Nạp cache cho toàn bộ các Chunk đã load sẵn trên server
        for (String worldName : getConfig().getStringList("enabled-worlds")) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    dataManager.loadChunk(world, chunk.getX(), chunk.getZ());
                }
            }
        }

        // Đăng ký Listener
        getServer().getPluginManager().registerEvents(new BlockEventListener(this), this);
        getServer().getPluginManager().registerEvents(new ExplosionListener(this), this);
        getServer().getPluginManager().registerEvents(new PistonListener(this), this);

        if (getCommand("pbs") != null) {
            getCommand("pbs").setExecutor(this);
        }

        getLogger().info("PlayerBlockShield da khoi chay thanh cong!");
    }

    @Override
    public void onDisable() {
        if (dataManager != null) {
            dataManager.close();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pbs.admin")) {
            sender.sendMessage(ChatColor.RED + "Ban khong co quyen!");
            return true;
        }

        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        getConfig().getString("messages.reload-success", "&aDa reload config!")));
                return true;
            }
            if (args[0].equalsIgnoreCase("clear")) {
                dataManager.clearAll();
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        getConfig().getString("messages.clear-success",
                                "&aDa xoa sach du lieu block nguoi choi! Toan bo map hien tai da duoc bao ve!")));
                return true;
            }
        }

        sender.sendMessage(ChatColor.YELLOW + "Su dung: /pbs <reload|clear>");
        return true;
    }

    public BlockDataManager getDataManager() {
        return dataManager;
    }
}
