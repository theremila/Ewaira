package dev.evasex.ewaira.model;

import dev.evasex.ewaira.ModIdentity;

public final class ModelGeometry {
    private static volatile boolean enabled = !ModIdentity.disabledBySystemProperty();

    private ModelGeometry() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }
}
