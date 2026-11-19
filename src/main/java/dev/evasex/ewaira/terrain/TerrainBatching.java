package dev.evasex.ewaira.terrain;

import dev.evasex.ewaira.ModIdentity;

/** Shared switch; the native command adapter is currently specific to 26.3. */
public final class TerrainBatching {
    private static volatile boolean enabled = !ModIdentity.disabledBySystemProperty();
    private TerrainBatching() {}
    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }
}
