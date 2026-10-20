package com.pbs.manager;

import com.pbs.PlayerBlockShield;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BlockDataManager {

    private final PlayerBlockShield plugin;
    private Connection connection;

    // Cache: WorldName -> ChunkKey -> Set<PackedBlockPos>
    private final Map<String, Map<Long, Set<Integer>>> chunkCache = new ConcurrentHashMap<>();

    // Hàng đợi duy nhất giữ đúng thứ tự thời gian (Đặt -> Phá -> Đặt)
    private final ConcurrentLinkedQueue<DbOperation> operationQueue = new ConcurrentLinkedQueue<>();

    private record DbOperation(String world, long chunkKey, int packedPos, boolean isInsert) {}

    public BlockDataManager(PlayerBlockShield plugin) {
        this.plugin = plugin;
        initDatabase();
        startBatchFlusher();
    }

    private synchronized void initDatabase() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();

            File dbFile = new File(dataFolder, "blocks.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            try (Statement st = connection.createStatement()) {
                st.execute("""
                    CREATE TABLE IF NOT EXISTS player_blocks (
                        world TEXT NOT NULL,
                        chunk_key INTEGER NOT NULL,
                        block_pos INTEGER NOT NULL,
                        PRIMARY KEY (world, chunk_key, block_pos)
                    )
                """);
                st.execute("CREATE INDEX IF NOT EXISTS idx_chunk ON player_blocks(world, chunk_key)");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Khong the khoi tao SQLite: " + e.getMessage());
        }
    }

    public static long getChunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public static int packPosition(int x, int y, int z) {
        return ((x & 0xF) << 15) | ((z & 0xF) << 11) | ((y + 128) & 0x7FF);
    }

    public synchronized void loadChunk(World world, int cx, int cz) {
        if (connection == null) return;

        // Đảm bảo mọi thao tác chờ trong hàng đợi đã được ghi xuống DB trước khi đọc
        if (!operationQueue.isEmpty()) {
            flushSync();
        }

        long key = getChunkKey(cx, cz);
        String worldName = world.getName();

        Set<Integer> set = chunkCache
                .computeIfAbsent(worldName, w -> new ConcurrentHashMap<>())
                .computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT block_pos FROM player_blocks WHERE world = ? AND chunk_key = ?")) {
            ps.setString(1, worldName);
            ps.setLong(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    set.add(rs.getInt("block_pos"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Loi doc chunk cache: " + e.getMessage());
        }
    }

    public synchronized void unloadChunk(World world, int cx, int cz) {
        flushSync();
        long key = getChunkKey(cx, cz);
        Map<Long, Set<Integer>> worldMap = chunkCache.get(world.getName());
        if (worldMap != null) {
            worldMap.remove(key);
        }
    }

    public boolean isPlayerPlaced(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        Map<Long, Set<Integer>> worldMap = chunkCache.get(loc.getWorld().getName());
        if (worldMap == null) return false;

        long cKey = getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        Set<Integer> blocks = worldMap.get(cKey);
        if (blocks == null) return false;

        int packed = packPosition(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        return blocks.contains(packed);
    }

    public void addBlock(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        String wName = loc.getWorld().getName();
        long cKey = getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        int packed = packPosition(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

        chunkCache.computeIfAbsent(wName, w -> new ConcurrentHashMap<>())
                  .computeIfAbsent(cKey, c -> ConcurrentHashMap.newKeySet())
                  .add(packed);

        operationQueue.add(new DbOperation(wName, cKey, packed, true));
    }

    public void removeBlock(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        String wName = loc.getWorld().getName();
        long cKey = getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        int packed = packPosition(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

        Map<Long, Set<Integer>> worldMap = chunkCache.get(wName);
        if (worldMap != null) {
            Set<Integer> blocks = worldMap.get(cKey);
            if (blocks != null) {
                blocks.remove(packed);
            }
        }

        operationQueue.add(new DbOperation(wName, cKey, packed, false));
    }

    public synchronized void clearAll() {
        operationQueue.clear();
        for (Map<Long, Set<Integer>> worldMap : chunkCache.values()) {
            for (Set<Integer> blockSet : worldMap.values()) {
                blockSet.clear();
            }
        }
        if (connection == null) return;
        try (Statement st = connection.createStatement()) {
            st.execute("DELETE FROM player_blocks");
        } catch (SQLException e) {
            plugin.getLogger().warning("Loi xoa database: " + e.getMessage());
        }
    }

    private void startBatchFlusher() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushSync, 100L, 100L);
    }

    public synchronized void flushSync() {
        if (connection == null || operationQueue.isEmpty()) return;
        try {
            connection.setAutoCommit(false);

            try (PreparedStatement insertPs = connection.prepareStatement(
                    "INSERT OR IGNORE INTO player_blocks (world, chunk_key, block_pos) VALUES (?, ?, ?)");
                 PreparedStatement deletePs = connection.prepareStatement(
                    "DELETE FROM player_blocks WHERE world = ? AND chunk_key = ? AND block_pos = ?")) {

                DbOperation op;
                while ((op = operationQueue.poll()) != null) {
                    if (op.isInsert()) {
                        insertPs.setString(1, op.world());
                        insertPs.setLong(2, op.chunkKey());
                        insertPs.setInt(3, op.packedPos());
                        insertPs.executeUpdate();
                    } else {
                        deletePs.setString(1, op.world());
                        deletePs.setLong(2, op.chunkKey());
                        deletePs.setInt(3, op.packedPos());
                        deletePs.executeUpdate();
                    }
                }
            }

            connection.commit();
        } catch (SQLException e) {
            plugin.getLogger().warning("Loi flush database: " + e.getMessage());
            try {
                connection.rollback();
            } catch (SQLException ignored) {}
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {}
        }
    }

    public synchronized void close() {
        flushSync();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {}
    }
}
