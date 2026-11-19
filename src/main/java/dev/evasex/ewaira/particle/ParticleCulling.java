package dev.evasex.ewaira.particle;

import dev.evasex.ewaira.ModIdentity;

/** Render-thread counters; this module is wired only on Minecraft 1.21.1. */
public final class ParticleCulling {
    private static volatile boolean enabled = !ModIdentity.disabledBySystemProperty();
    private static long checked;
    private static long skipped;

    private ParticleCulling() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }

    public static void record(boolean visible) {
        checked++;
        if (!visible) skipped++;
    }

    public static String statistics() {
        if (checked == 0) return "particles: inactive";
        return "particles=" + (enabled ? "ON" : "OFF") + "; checked=" + checked + ", skipped=" + skipped;
    }

    /** A rotated unit quaternion keeps each corner within sqrt(2) * size of its center. */
    public static double radius(float size) {
        return Math.abs((double) size) * 1.5 + 0.01;
    }
}
