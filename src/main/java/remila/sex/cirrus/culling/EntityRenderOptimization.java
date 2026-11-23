package remila.sex.cirrus.culling;

/**
 * Render-thread entity crowd optimization:
 * - Zero-allocation per-frame 2x2-block spatial shadow cap (max 8 shadows per cell, max distance 24 blocks).
 * - Frame epoch counter for per-renderer EntityRenderState pooling.
 */
public final class EntityRenderOptimization {
    public static final double MAX_SHADOW_DISTANCE_SQ = 24.0 * 24.0;
    public static final int MAX_SHADOWS_PER_CELL = 8;

    private static final int TABLE_SIZE = 2048;
    private static final int TABLE_MASK = TABLE_SIZE - 1;
    private static final long[] CELL_KEYS = new long[TABLE_SIZE];
    private static final int[] CELL_EPOCHS = new int[TABLE_SIZE];
    private static final int[] CELL_COUNTS = new int[TABLE_SIZE];
    private static int frameEpoch = 1;

    private EntityRenderOptimization() {}

    public static void beginFrame() {
        frameEpoch++;
        if (frameEpoch == 0) {
            java.util.Arrays.fill(CELL_EPOCHS, 0);
            frameEpoch = 1;
        }
    }

    public static int frameEpoch() {
        return frameEpoch;
    }

    /**
     * Returns true if this entity should extract ground shadows on the current frame.
     * Enforces both a 24-block distance cap and a max of 8 shadows per 2x2-block cell to stop
     * dense mob crowds from performing thousands of redundant ground block queries per frame.
     */
    public static boolean shouldExtractShadow(double x, double z, double distanceToCameraSq) {
        if (!EntityOcclusionEngine.isEnabled()) return true;
        if (distanceToCameraSq > MAX_SHADOW_DISTANCE_SQ) return false;

        int cellX = floor(x) >> 1;
        int cellZ = floor(z) >> 1;
        long key = ((long) cellX << 32) ^ (cellZ & 0xFFFFFFFFL);
        int slot = mix(key) & TABLE_MASK;
        int epoch = frameEpoch;
        if (CELL_EPOCHS[slot] != epoch || CELL_KEYS[slot] != key) {
            CELL_EPOCHS[slot] = epoch;
            CELL_KEYS[slot] = key;
            CELL_COUNTS[slot] = 1;
            return true;
        }
        int count = CELL_COUNTS[slot];
        if (count >= MAX_SHADOWS_PER_CELL) return false;
        CELL_COUNTS[slot] = count + 1;
        return true;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static int mix(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        h ^= h >>> 32;
        return (int) (h ^ (h >>> 16));
    }
}
