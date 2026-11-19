package dev.evasex.ewaira.lod;

/** Stable screen-space policy; never changes the user's render distance, FOV or frame limit. */
public final class AutomaticTuning {
    private AutomaticTuning() {}
    public record Distances(double terrain, double items) {}
    public static Distances distances(int renderChunks, int height, double fov) {
        int chunks = Math.max(2, Math.min(64, renderChunks));
        if (height <= 0 || !Double.isFinite(fov) || fov < 10 || fov > 170) return new Distances(64, 32);
        double scale = height / 720.0 * Math.tan(Math.toRadians(35)) / Math.tan(Math.toRadians(fov / 2));
        double terrain = Math.max(32, Math.min(Math.min(512, chunks * 16), Math.rint(64 * scale / 8) * 8));
        double items = Math.max(16, Math.min(128, Math.rint(32 * scale / 4) * 4));
        return new Distances(terrain, items);
    }
    public static void apply(int renderChunks, int height, double fov) {
        Distances distances = distances(renderChunks, height, fov);
        TerrainLod.setDistance(distances.terrain());
        ItemLod.setDistance(distances.items());
    }
}
