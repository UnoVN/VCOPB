package com.pbs;

import com.pbs.hook.WorldGuardHook;
import com.pbs.listener.BlockEventListener;
import com.pbs.listener.ExplosionListener;
import com.pbs.listener.PistonListener;
import com.pbs.manager.BlockDataManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayerBlockShield extends JavaPlugin implements CommandExecutor {

    private BlockDataManager dataManager;

    @Override
    public void onLoad() {
        // Đăng ký Custom Flag bắt buộc phải ở onLoad
        WorldGuardHook.registerFlag();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.dataManager = new BlockDataManager(this);

        // Đăng ký các Listener
        getServer().getPluginManager().registerEvents(new BlockEventListener(this), this);
        getServer().getPluginManager().registerEvents(new ExplosionListener(this), this);
        getServer().getPluginManager().registerEvents(new PistonListener(this), this);

        if (getCommand("pbs") != null) {
            getCommand("pbs").setExecutor(this);
        }

        getLogger().info("PlayerBlockShield da duoc khoi chay thanh cong!");
    }

    @Override
    public void onDisable() {
        if (dataManager != null) {
            dataManager.close();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("pbs.admin")) {
                sender.sendMessage(ChatColor.RED + "Ban khong co quyen!");
                return true;
            }
            reloadConfig();
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    getConfig().getString("messages.reload-success", "&aDa reload config!")));
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "Su dung: /pbs reload");
        return true;
    }

    public BlockDataManager getDataManager() {
        return dataManager;
    }
}
