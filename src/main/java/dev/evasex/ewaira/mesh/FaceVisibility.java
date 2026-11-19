package dev.evasex.ewaira.mesh;

import dev.evasex.ewaira.ModIdentity;

/** Runtime is automatic; the switch is used only by development verification. */
public final class FaceVisibility {
    private static volatile boolean enabled = !ModIdentity.disabledBySystemProperty();
    private FaceVisibility() {}
    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }
}
