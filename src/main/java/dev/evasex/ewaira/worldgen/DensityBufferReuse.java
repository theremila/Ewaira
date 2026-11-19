package dev.evasex.ewaira.worldgen;

import dev.evasex.ewaira.ModIdentity;

public final class DensityBufferReuse {
    private static volatile boolean enabled = !ModIdentity.disabledBySystemProperty();

    private DensityBufferReuse() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }
}
