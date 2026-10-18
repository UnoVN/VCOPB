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

public class WorldGuardHook {

    public static StateFlag ONLY_PLAYER_BLOCKS;

    public static void registerFlag() {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            // Đăng ký với giá trị mặc định là ALLOW
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

    /**
     * Kiểm tra xem tại vị trí này plugin có quản trị việc bảo vệ khối hay không.
     * Lưu ý: Không truyền LocalPlayer vào queryState để tránh bị ảnh hưởng bởi
     * quyền thành viên của Region.
     */
    public static boolean isShieldActiveAt(PlayerBlockShield plugin, Location loc) {
        if (loc.getWorld() == null) return false;

        // 1. Kiểm tra danh sách thế giới áp dụng
        if (!plugin.getConfig().getStringList("enabled-worlds").contains(loc.getWorld().getName())) {
            return false;
        }

        // 2. Kiểm tra Region WorldGuard
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));

        String mode = plugin.getConfig().getString("application-mode", "WORLD_WIDE");

        if ("REGION_ONLY".equalsIgnoreCase(mode)) {
            // Chỉ bật nếu region có gán flag allow
            return set.queryState(null, ONLY_PLAYER_BLOCKS) == StateFlag.State.ALLOW;
        } else {
            // WORLD_WIDE: Bật mọi nơi, trừ khi region bị set deny
            return set.queryState(null, ONLY_PLAYER_BLOCKS) != StateFlag.State.DENY;
        }
    }
}
