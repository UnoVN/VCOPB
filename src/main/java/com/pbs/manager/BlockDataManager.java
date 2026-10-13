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

    // Queue Batch I/O
    private final ConcurrentLinkedQueue<Record> insertQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Record> deleteQueue = new ConcurrentLinkedQueue<>();

    private record Record(String world, long chunkKey, int packedPos) {}

    public BlockDataManager(PlayerBlockShield plugin) {
        this.plugin = plugin;
        initDatabase();
        startBatchFlusher();
    }

    private void initDatabase() {
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
                    );
                    CREATE INDEX IF NOT EXISTS idx_chunk ON player_blocks(world, chunk_key);
                """);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Khong the khoi tao SQLite: " + e.getMessage());
        }
    }

    public static long getChunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    // Nen toa do relative vao 1 so Integer duy nhat (-128 <= Y <= 1920)
    public static int packPosition(int x, int y, int z) {
        return ((x & 0xF) << 15) | ((z & 0xF) << 11) | ((y + 128) & 0x7FF);
    }

    public void loadChunk(World world, int cx, int cz) {
        long key = getChunkKey(cx, cz);
        String worldName = world.getName();

        Set<Integer> set = ConcurrentHashMap.newKeySet();
        chunkCache.computeIfAbsent(worldName, w -> new ConcurrentHashMap<>()).put(key, set);

        // Load sync nhe tu DB (chi doc index ~0.05ms)
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT block_pos FROM player_blocks WHERE world = ? AND chunk_key = ?")) {
            ps.setString(1, worldName);
            ps.setLong(2, key);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                set.add(rs.getInt("block_pos"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Loi doc chunk cache: " + e.getMessage());
        }
    }

    public void unloadChunk(World world, int cx, int cz) {
        long key = getChunkKey(cx, cz);
        Map<Long, Set<Integer>> worldMap = chunkCache.get(world.getName());
        if (worldMap != null) {
            worldMap.remove(key);
        }
    }

    public boolean isPlayerPlaced(Location loc) {
        if (loc.getWorld() == null) return false;
        Map<Long, Set<Integer>> worldMap = chunkCache.get(loc.getWorld().getName());
        if (worldMap == null) return false;

        long cKey = getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        Set<Integer> blocks = worldMap.get(cKey);
        if (blocks == null) return false;

        int packed = packPosition(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        return blocks.contains(packed);
    }

    public void addBlock(Location loc) {
        if (loc.getWorld() == null) return;
        String wName = loc.getWorld().getName();
        long cKey = getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        int packed = packPosition(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

        chunkCache.computeIfAbsent(wName, w -> new ConcurrentHashMap<>())
                  .computeIfAbsent(cKey, c -> ConcurrentHashMap.newKeySet())
                  .add(packed);

        insertQueue.add(new Record(wName, cKey, packed));
    }

    public void removeBlock(Location loc) {
        if (loc.getWorld() == null) return;
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

        deleteQueue.add(new Record(wName, cKey, packed));
    }

    private void startBatchFlusher() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushSync, 100L, 100L); // 5 giay ghi DB 1 lan
    }

    public synchronized void flushSync() {
        if (connection == null) return;
        try {
            connection.setAutoCommit(false);

            // Ghi batch inserts
            if (!insertQueue.isEmpty()) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT OR IGNORE INTO player_blocks (world, chunk_key, block_pos) VALUES (?, ?, ?)")) {
                    Record r;
                    while ((r = insertQueue.poll()) != null) {
                        ps.setString(1, r.world());
                        ps.setLong(2, r.chunkKey());
                        ps.setInt(3, r.packedPos());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            // Ghi batch deletes
            if (!deleteQueue.isEmpty()) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM player_blocks WHERE world = ? AND chunk_key = ? AND block_pos = ?")) {
                    Record r;
                    while ((r = deleteQueue.poll()) != null) {
                        ps.setString(1, r.world());
                        ps.setLong(2, r.chunkKey());
                        ps.setInt(3, r.packedPos());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            connection.commit();
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            plugin.getLogger().warning("Loi flush database: " + e.getMessage());
        }
    }

    public void close() {
        flushSync();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {}
    }
}
