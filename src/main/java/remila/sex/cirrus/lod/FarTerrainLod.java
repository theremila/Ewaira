package remila.sex.cirrus.lod;

import remila.sex.cirrus.ModIdentity;

/** Screen-space policy for terrain tiles outside the detailed client mesh. */
public final class FarTerrainLod {
    public static final int CHUNK_SIZE = 16;
    public static final int MAX_DISTANCE_CHUNKS = 64;
    public static final int SAMPLE_STEP = 2;
    public static final int SAMPLES_PER_SIDE = CHUNK_SIZE / SAMPLE_STEP + 1;
    private static final double MAX_EDGE_PIXELS = 12.0;
    private static final double MAX_ERROR_PIXELS = 1.5;
    private static volatile boolean enabled = !ModIdentity.disabledBySystemProperty();

    private FarTerrainLod() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }

    public static int focalPixels(int framebufferWidth, int framebufferHeight, float verticalProjectionScale) {
        if (framebufferWidth <= 0 || framebufferHeight <= 0
                || !Float.isFinite(verticalProjectionScale) || verticalProjectionScale <= 0) return 0;
        double pixels = framebufferHeight * 0.5 * verticalProjectionScale;
        return (int) Math.max(1, Math.min(65_536, Math.round(pixels)));
    }

    public static boolean fits(int step, int heightError, double distance, int focalPixels) {
        double pixelsPerBlock = focalPixels / Math.max(1.0, distance);
        return step * pixelsPerBlock <= MAX_EDGE_PIXELS && heightError * pixelsPerBlock <= MAX_ERROR_PIXELS;
    }
}
