package dev.evasex.ewaira.lod;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/** Previously loaded terrain. Region revisions cover tile contents and neighboring coverage. */
public final class FarTerrainCache {
    static final int REGION_CHUNKS = 8;
    static final int WEST = 1, EAST = 2, NORTH = 4, SOUTH = 8;
    private static final int MAX_TILES = 16_384;
    private final Long2ObjectLinkedOpenHashMap<FarTerrainTile> tiles = new Long2ObjectLinkedOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Region> regions = new Long2ObjectOpenHashMap<>();

    public void remember(LevelChunk chunk) {
        FarTerrainTile tile = new FarTerrainTile(chunk);
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
            old.tiles.remove(ChunkPos.pack(removed.x, removed.z));
            old.invalidate();
            if (old.tiles.isEmpty()) regions.remove(old.key);
            invalidateAdjacent(removed, old);
        }
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
        private AABB bounds;

        Region(long key, int x, int z) {
            this.key = key;
            this.x = Math.floorDiv(x, REGION_CHUNKS) * REGION_CHUNKS;
            this.z = Math.floorDiv(z, REGION_CHUNKS) * REGION_CHUNKS;
        }

        void invalidate() {
            revision++;
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
