package remila.sex.cirrus.lod;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/**
 * Previously loaded terrain grouped into 16x16-chunk regions (4x fewer GPU draws than 8x8)
 * with optional asynchronous compressed disk persistence to eliminate horizon holes.
 */
public final class FarTerrainCache {
    static final int REGION_CHUNKS = 16;
    static final int WEST = 1, EAST = 2, NORTH = 4, SOUTH = 8;
    private static final int MAX_TILES = 32_768;

    private final Long2ObjectLinkedOpenHashMap<FarTerrainTile> tiles = new Long2ObjectLinkedOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Region> regions = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet diskRequested = new LongOpenHashSet();
    private final ConcurrentLinkedQueue<List<FarTerrainTile>> pendingDiskLoads = new ConcurrentLinkedQueue<>();
    private FarTerrainDiskCache diskCache;

    public void attachDiskCache(Path directory) {
        if (directory != null && diskCache == null) {
            diskCache = new FarTerrainDiskCache(directory);
        }
    }

    public void remember(LevelChunk chunk) {
        FarTerrainTile tile = new FarTerrainTile(chunk);
        Region region = insertTile(tile);
        if (diskCache != null && region != null && ++region.unsavedWrites >= 16) {
            region.unsavedWrites = 0;
            diskCache.saveAsync(region.x, region.z, new ArrayList<>(region.tiles.values()));
        }
    }

    void flushDirtyToDisk() {
        if (diskCache == null) return;
        for (Region region : regions.values()) {
            if (region.unsavedWrites > 0) {
                region.unsavedWrites = 0;
                diskCache.saveAsync(region.x, region.z, new ArrayList<>(region.tiles.values()));
            }
        }
    }

    void prefetchAround(int centerChunkX, int centerChunkZ, int radiusChunks) {
        drainDiskLoads();
        if (diskCache == null) return;
        int minRx = Math.floorDiv(centerChunkX - radiusChunks, REGION_CHUNKS);
        int maxRx = Math.floorDiv(centerChunkX + radiusChunks, REGION_CHUNKS);
        int minRz = Math.floorDiv(centerChunkZ - radiusChunks, REGION_CHUNKS);
        int maxRz = Math.floorDiv(centerChunkZ + radiusChunks, REGION_CHUNKS);
        int budget = 4;
        for (int rx = minRx; rx <= maxRx && budget > 0; rx++) {
            for (int rz = minRz; rz <= maxRz && budget > 0; rz++) {
                long rKey = ChunkPos.pack(rx, rz);
                if (!diskRequested.add(rKey)) continue;
                budget--;
                diskCache.loadAsync(rx * REGION_CHUNKS, rz * REGION_CHUNKS, pendingDiskLoads::add);
            }
        }
    }

    private void drainDiskLoads() {
        List<FarTerrainTile> batch;
        while ((batch = pendingDiskLoads.poll()) != null) {
            for (FarTerrainTile tile : batch) {
                long key = ChunkPos.pack(tile.x, tile.z);
                if (!tiles.containsKey(key)) insertTile(tile);
            }
        }
    }

    private Region insertTile(FarTerrainTile tile) {
        long key = ChunkPos.pack(tile.x, tile.z);
        boolean added = tiles.putAndMoveToLast(key, tile) == null;
        long regionKey = regionKey(tile.x, tile.z);
        Region region = regions.get(regionKey);
        if (region == null) {
            region = new Region(regionKey, tile.x, tile.z);
            regions.put(regionKey, region);
        }
        region.tiles.put(key, tile);
        region.invalidate();
        if (added) invalidateAdjacent(tile, region);
        if (tiles.size() > MAX_TILES) {
            FarTerrainTile removed = tiles.removeFirst();
            Region old = regions.get(regionKey(removed.x, removed.z));
            if (old != null) {
                old.tiles.remove(ChunkPos.pack(removed.x, removed.z));
                old.invalidate();
                if (old.tiles.isEmpty()) regions.remove(old.key);
                invalidateAdjacent(removed, old);
            }
        }
        return region;
    }

    private void invalidateAdjacent(FarTerrainTile tile, Region owner) {
        if (tile.x == owner.x) invalidateAt(tile.x - 1, tile.z);
        if (tile.x == owner.x + REGION_CHUNKS - 1) invalidateAt(tile.x + 1, tile.z);
        if (tile.z == owner.z) invalidateAt(tile.x, tile.z - 1);
        if (tile.z == owner.z + REGION_CHUNKS - 1) invalidateAt(tile.x, tile.z + 1);
    }

    private void invalidateAt(int x, int z) {
        Region region = regions.get(regionKey(x, z));
        if (region != null) region.invalidate();
    }

    private static long regionKey(int x, int z) {
        return ChunkPos.pack(Math.floorDiv(x, REGION_CHUNKS), Math.floorDiv(z, REGION_CHUNKS));
    }

    Collection<Region> regions() { return regions.values(); }
    Region region(long key) { return regions.get(key); }

    List<Sample> snapshot(Region region) {
        var result = new ArrayList<Sample>(region.tiles.size());
        for (FarTerrainTile tile : region.tiles.values()) {
            int mask = 0;
            if (tiles.containsKey(ChunkPos.pack(tile.x - 1, tile.z))) mask |= WEST;
            if (tiles.containsKey(ChunkPos.pack(tile.x + 1, tile.z))) mask |= EAST;
            if (tiles.containsKey(ChunkPos.pack(tile.x, tile.z - 1))) mask |= NORTH;
            if (tiles.containsKey(ChunkPos.pack(tile.x, tile.z + 1))) mask |= SOUTH;
            result.add(new Sample(tile, mask));
        }
        return result;
    }

    record Sample(FarTerrainTile tile, int neighbors) {}

    static final class Region {
        final long key;
        final int x, z;
        final Long2ObjectOpenHashMap<FarTerrainTile> tiles = new Long2ObjectOpenHashMap<>();
        long revision;
        long lastDirtyMs;
        int dirtyTiles;
        int unsavedWrites;
        private AABB bounds;

        Region(long key, int x, int z) {
            this.key = key;
            this.x = Math.floorDiv(x, REGION_CHUNKS) * REGION_CHUNKS;
            this.z = Math.floorDiv(z, REGION_CHUNKS) * REGION_CHUNKS;
        }

        void invalidate() {
            revision++;
            dirtyTiles++;
            lastDirtyMs = System.currentTimeMillis();
            bounds = null;
        }

        AABB bounds() {
            if (bounds != null) return bounds;
            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            for (FarTerrainTile tile : tiles.values()) {
                minY = Math.min(minY, tile.minHeight);
                maxY = Math.max(maxY, tile.maxHeight);
            }
            int size = FarTerrainLod.CHUNK_SIZE;
            bounds = new AABB(x * size, minY - FarTerrainMesh.EDGE_SKIRT, z * size,
                    (x + REGION_CHUNKS) * size, maxY, (z + REGION_CHUNKS) * size);
            return bounds;
        }
    }
}
