package remila.sex.cirrus.occlusion;

/** Opt-in until in-world visuals and frame times are verified. Counters belong to the render thread. */
public final class EntityOcclusion {
    private static volatile boolean enabled;
    private static long checked;
    private static long skipped;
    private EntityOcclusion() {}
    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }
    public static void record(boolean hidden) { checked++; if (hidden) skipped++; }
    public static String statistics() {
        return "occlusion=" + (enabled ? "ON" : "OFF") + "; checked=" + checked + ", skipped=" + skipped;
    }
}
