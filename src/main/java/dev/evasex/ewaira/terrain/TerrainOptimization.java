package dev.evasex.ewaira.terrain;

import dev.evasex.ewaira.ModIdentity;

public final class TerrainOptimization {
    private static volatile boolean enabled = !ModIdentity.disabledBySystemProperty();
    private static volatile double margin = 1.0 / 16;
    public static double margin() { return margin; }
    public static void setMargin(double value) {
        if (!Double.isFinite(value) || value < 1.0 / 16 || value > 1) throw new IllegalArgumentException("Invalid terrain margin");
        margin = value;
    }
    private TerrainOptimization() {}
    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }
}
