package dev.evasex.ewaira.lod;

import dev.evasex.ewaira.ModIdentity;

/** Render-thread selection; rebuilding and GPU ownership remain native. */
public final class TerrainLod {
    private static volatile boolean enabled = !ModIdentity.disabledBySystemProperty();
    private static volatile double distance = 64;

    private TerrainLod() {}
    public static double distance() { return distance; }
    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }
    public static void setDistance(double value) {
        if (!Double.isFinite(value) || value < 32 || value > 512) throw new IllegalArgumentException("Terrain LOD distance must be 32..512 blocks");
        distance = value;
    }
    public static int level(double x, double y, double z) {
        if (!enabled || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return 0;
        double dx = Math.max(0, Math.max(-x, x - 16));
        double dy = Math.max(0, Math.max(-y, y - 16));
        double dz = Math.max(0, Math.max(-z, z - 16));
        double squared = dx * dx + dy * dy + dz * dz;
        double threshold = distance * distance;
        return squared >= threshold * 4 ? 2 : squared >= threshold ? 1 : 0;
    }
}
