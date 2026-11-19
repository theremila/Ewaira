package dev.evasex.ewaira.lod;

import static dev.evasex.ewaira.lod.FarTerrainLod.CHUNK_SIZE;
import static dev.evasex.ewaira.lod.FarTerrainLod.SAMPLES_PER_SIDE;
import static dev.evasex.ewaira.lod.FarTerrainLod.SAMPLE_STEP;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import static dev.evasex.ewaira.lod.FarTerrainCache.WEST;
import static dev.evasex.ewaira.lod.FarTerrainCache.EAST;
import static dev.evasex.ewaira.lod.FarTerrainCache.NORTH;
import static dev.evasex.ewaira.lod.FarTerrainCache.SOUTH;
import java.util.List;

/** Built from immutable tile samples on a worker. Upload and release belong to the renderer. */
record FarTerrainMesh(ByteBufferBuilder arena, MeshData mesh) implements AutoCloseable {
    private static final int MAX_VERTICES = FarTerrainCache.REGION_CHUNKS * FarTerrainCache.REGION_CHUNKS * 6 * (64 + 32);
    private static final int VERTEX_BYTES = 16;
    static final int EDGE_SKIRT = 8;

    static FarTerrainMesh build(List<FarTerrainCache.Sample> tiles, int regionX, int regionZ, int distance, int focalPixels) {
        if (tiles.isEmpty()) return null;
        int maxSample = SAMPLES_PER_SIDE - 1;
        var arena = new ByteBufferBuilder(Math.max(1024, 512 * tiles.size()), (long) MAX_VERTICES * VERTEX_BYTES);
        boolean transferred = false;
        try {
            var vertices = new BufferBuilder(arena, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            for (FarTerrainCache.Sample sample : tiles) {
                FarTerrainTile tile = sample.tile();
                int stride = tile.sampleStride(distance, focalPixels);
                int neighbors = sample.neighbors();
                var surface = new Surface(vertices, tile, (tile.x - regionX) * CHUNK_SIZE, (tile.z - regionZ) * CHUNK_SIZE, neighbors);
                for (int row = 0; row < maxSample; row += stride) {
                    for (int column = 0; column < maxSample; column += stride) {
                        surface.quad(column, row, column + stride, row + stride);
                    }
                    if ((neighbors & WEST) != 0) surface.skirt(0, row, 0, row + stride);
                    if ((neighbors & EAST) != 0) surface.skirt(maxSample, row, maxSample, row + stride);
                    if ((neighbors & NORTH) != 0) surface.skirt(row, 0, row + stride, 0);
                    if ((neighbors & SOUTH) != 0) surface.skirt(row, maxSample, row + stride, maxSample);
                }
            }
            MeshData mesh = vertices.build();
            if (mesh == null) return null;
            var result = new FarTerrainMesh(arena, mesh);
            transferred = true;
            return result;
        } finally {
            if (!transferred) arena.close();
        }
    }

    private record Surface(BufferBuilder vertices, FarTerrainTile tile, int x, int z, int neighbors) {
        void quad(int x0, int z0, int x1, int z1) {
            vertex(x0, z0, 0);
            vertex(x0, z1, 0);
            vertex(x1, z1, 0);
            vertex(x0, z0, 0);
            vertex(x1, z1, 0);
            vertex(x1, z0, 0);
        }

        void skirt(int x0, int z0, int x1, int z1) {
            vertex(x0, z0, 0);
            vertex(x1, z1, 0);
            vertex(x1, z1, EDGE_SKIRT);
            vertex(x0, z0, 0);
            vertex(x1, z1, EDGE_SKIRT);
            vertex(x0, z0, EDGE_SKIRT);
        }

        private void vertex(int column, int row, int inset) {
            int last = SAMPLES_PER_SIDE - 1;
            boolean edge = (column == 0 && (neighbors & WEST) == 0) || (column == last && (neighbors & EAST) == 0)
                    || (row == 0 && (neighbors & NORTH) == 0) || (row == last && (neighbors & SOUTH) == 0);
            int color = tile.color(column, row) | (edge ? 0 : 0xff000000);
            vertices.addVertex(x + column * SAMPLE_STEP, tile.height(column, row) - inset, z + row * SAMPLE_STEP)
                    .setColor(color);
        }
    }

    @Override
    public void close() {
        try {
            mesh.close();
        } finally {
            arena.close();
        }
    }
}
