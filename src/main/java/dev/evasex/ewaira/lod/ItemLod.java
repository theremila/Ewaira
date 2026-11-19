package dev.evasex.ewaira.lod;

import dev.evasex.ewaira.ModIdentity;

/** Visual stack replicas only: item contents and simulation remain untouched. */
public final class ItemLod {
    private static volatile boolean enabled = !ModIdentity.disabledBySystemProperty();
    private static volatile double distanceSquared = 32 * 32;
    private static final ThreadLocal<Context> CONTEXT = ThreadLocal.withInitial(Context::new);
    private ItemLod() {}
    public static void setEnabled(boolean value) { enabled = value; }
    public static boolean enabled() { return enabled; }
    public static void setDistance(double distance) {
        if (!Double.isFinite(distance) || distance < 8 || distance > 256) throw new IllegalArgumentException("Item LOD distance must be 8..256 blocks");
        distanceSquared = distance * distance;
    }
    public static int count(int original, double distance) {
        if (!enabled || !Double.isFinite(distance) || distance < 0) return original;
        double threshold = distanceSquared;
        if (distance >= threshold) return Math.min(original, 1);
        if (distance >= threshold * 0.25) return Math.min(original, 2);
        return original;
    }
    public static double enter(double distance) {
        Context context = CONTEXT.get();
        double previous = context.distance;
        context.distance = distance;
        return previous;
    }
    public static void restore(double previous) { CONTEXT.get().distance = previous; }
    public static int scopedCount(int original) { return count(original, CONTEXT.get().distance); }
    private static final class Context { double distance = Double.NaN; }
}
