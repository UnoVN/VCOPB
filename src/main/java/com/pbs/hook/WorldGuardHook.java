package com.pbs.hook;

import com.pbs.PlayerBlockShield;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class WorldGuardHook {

    public static StateFlag ONLY_PLAYER_BLOCKS;

    public static void registerFlag() {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            StateFlag flag = new StateFlag("break-only-player-blocks", false);
            registry.register(flag);
            ONLY_PLAYER_BLOCKS = flag;
        } catch (FlagConflictException e) {
            Flag<?> existing = registry.get("break-only-player-blocks");
            if (existing instanceof StateFlag stateFlag) {
                ONLY_PLAYER_BLOCKS = stateFlag;
            }
        }
    }

    public static boolean shouldProtect(PlayerBlockShield plugin, Location loc, Player player) {
        if (loc.getWorld() == null) return false;

        // 1. Kiem tra world
        if (!plugin.getConfig().getStringList("enabled-worlds").contains(loc.getWorld().getName())) {
            return false;
        }

        // 2. Kiem tra bypass
        if (player != null && player.hasPermission("pbs.bypass")) {
            return false;
        }

        // 3. Kiem tra WorldGuard Region
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));

        String mode = plugin.getConfig().getString("application-mode", "WORLD_WIDE");
        var wgPlayer = player != null ? WorldGuardPlugin.inst().wrapPlayer(player) : null;

        if ("REGION_ONLY".equalsIgnoreCase(mode)) {
            return set.testState(wgPlayer, ONLY_PLAYER_BLOCKS);
        } else {
            return set.queryState(wgPlayer, ONLY_PLAYER_BLOCKS) != StateFlag.State.DENY;
        }
    }
}
