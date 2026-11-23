package remila.sex.cirrus.lod;

import static remila.sex.cirrus.lod.FarTerrainLod.CHUNK_SIZE;
import static remila.sex.cirrus.lod.FarTerrainLod.SAMPLES_PER_SIDE;
import static remila.sex.cirrus.lod.FarTerrainLod.SAMPLE_STEP;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

final class FarTerrainTile {
    static final int GRID_LEN = SAMPLES_PER_SIDE * SAMPLES_PER_SIDE;
    final int x, z, minHeight, maxHeight;
    private final short[] heights = new short[GRID_LEN];
    private final int[] colors = new int[GRID_LEN];
    private volatile int[] stepErrors;

    FarTerrainTile(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        x = pos.x();
        z = pos.z();
        int minX = pos.getMinBlockX(), minZ = pos.getMinBlockZ();
        var sample = new BlockPos.MutableBlockPos();
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int row = 0; row < SAMPLES_PER_SIDE; row++) {
            for (int column = 0; column < SAMPLES_PER_SIDE; column++) {
                int localX = Math.min(CHUNK_SIZE - 1, column * SAMPLE_STEP);
                int localZ = Math.min(CHUNK_SIZE - 1, row * SAMPLE_STEP);
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
                sample.set(minX + localX, y, minZ + localZ);
                int index = row * SAMPLES_PER_SIDE + column;
                int h = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, y + 1));
                heights[index] = (short) h;
                min = Math.min(min, h);
                max = Math.max(max, h);
                MapColor color = chunk.getBlockState(sample).getMapColor(chunk, sample);
                colors[index] = color == MapColor.WATER ? 0x486a96 : color.col;
            }
        }
        minHeight = min;
        maxHeight = max;
    }

    FarTerrainTile(int x, int z, short[] heights, int[] colors) {
        this.x = x;
        this.z = z;
        System.arraycopy(heights, 0, this.heights, 0, GRID_LEN);
        System.arraycopy(colors, 0, this.colors, 0, GRID_LEN);
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int i = 0; i < GRID_LEN; i++) {
            int h = this.heights[i];
            if (h < min) min = h;
            if (h > max) max = h;
        }
        this.minHeight = min;
        this.maxHeight = max;
    }

    short[] rawHeights() { return heights; }
    int[] rawColors() { return colors; }

    private int heightError(int span) {
        int error = 0;
        for (int row = 0; row < SAMPLES_PER_SIDE; row++) {
            int z = Math.min(row / span * span, SAMPLES_PER_SIDE - 1 - span);
            int dz = row - z;
            for (int column = 0; column < SAMPLES_PER_SIDE; column++) {
                int x = Math.min(column / span * span, SAMPLES_PER_SIDE - 1 - span);
                int dx = column - x;
                int h00 = height(x, z), h11 = height(x + span, z + span);
                int otherWeight = Math.abs(dx - dz);
                int other = dx >= dz ? height(x + span, z) : height(x, z + span);
                int interpolationError = Math.abs(height(column, row) * span
                        - h00 * (span - Math.max(dx, dz)) - other * otherWeight - h11 * Math.min(dx, dz));
                error = Math.max(error, (interpolationError + span - 1) / span);
            }
        }
        return error;
    }

    int sampleStride(double distance, int focalPixels) {
        int minStride = distance >= 768.0 ? 4 : (distance >= 512.0 ? 2 : 1);
        int[] errors = stepErrors;
        if (errors == null) {
            errors = new int[] {heightError(2), heightError(4), heightError(8)};
            stepErrors = errors;
        }
        for (int tier = errors.length - 1; tier >= 0; tier--) {
            int stride = 2 << tier;
            if (stride <= minStride) return minStride;
            if (FarTerrainLod.fits(stride * SAMPLE_STEP, errors[tier], distance, focalPixels)) return stride;
        }
        return minStride;
    }

    int height(int x, int z) { return heights[z * SAMPLES_PER_SIDE + x]; }
    int color(int x, int z) { return colors[z * SAMPLES_PER_SIDE + x]; }
}
