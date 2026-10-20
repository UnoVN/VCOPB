package com.pbs.hook;

import com.pbs.PlayerBlockShield;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.World;

public class WorldGuardHook {

    public static StateFlag ONLY_PLAYER_BLOCKS;

    public static void registerFlag() {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            StateFlag flag = new StateFlag("break-only-player-blocks", true);
            registry.register(flag);
            ONLY_PLAYER_BLOCKS = flag;
        } catch (FlagConflictException e) {
            Flag<?> existing = registry.get("break-only-player-blocks");
            if (existing instanceof StateFlag stateFlag) {
                ONLY_PLAYER_BLOCKS = stateFlag;
            }
        }
    }

    public static boolean isWorldEnabled(PlayerBlockShield plugin, World world) {
        if (world == null) return false;
        return plugin.getConfig().getStringList("enabled-worlds").contains(world.getName());
    }

    public static boolean isShieldActiveAt(PlayerBlockShield plugin, Location loc) {
        if (loc == null || !isWorldEnabled(plugin, loc.getWorld())) {
            return false;
        }

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));

        String mode = plugin.getConfig().getString("application-mode", "WORLD_WIDE");

        if ("REGION_ONLY".equalsIgnoreCase(mode)) {
            return set.queryState(null, ONLY_PLAYER_BLOCKS) == StateFlag.State.ALLOW;
        } else {
            return set.queryState(null, ONLY_PLAYER_BLOCKS) != StateFlag.State.DENY;
        }
    }
}
