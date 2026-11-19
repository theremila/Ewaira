package dev.evasex.ewaira.mesh;

import dev.evasex.ewaira.ModIdentity;

public final class QuadEncoding {
    private static volatile boolean enabled = !ModIdentity.disabledBySystemProperty();
    private static volatile boolean available;

    private QuadEncoding() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }
    public static void markAvailable() { available = true; }
    public static String statistics() {
        return available ? "quad encoding=" + (enabled ? "ON" : "OFF") : "quad encoding: inactive";
    }
}
