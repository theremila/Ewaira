package remila.sex.cirrus.mesh;

import remila.sex.cirrus.ModIdentity;

public final class ChunkLighting {
    private static volatile boolean enabled = !ModIdentity.disabledBySystemProperty();
    private static volatile boolean available;

    private ChunkLighting() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }
    public static void markAvailable() { available = true; }
    public static String statistics() {
        return available ? "chunk lighting=" + (enabled ? "ON" : "OFF") : "chunk lighting: inactive";
    }
}
