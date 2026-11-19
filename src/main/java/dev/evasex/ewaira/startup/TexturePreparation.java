package dev.evasex.ewaira.startup;

import dev.evasex.ewaira.ModIdentity;

/** Controls lossless texture preparation during startup and resource reloads. */
public final class TexturePreparation {
    private static volatile boolean enabled = !ModIdentity.disabledBySystemProperty();
    private TexturePreparation() {}
    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }
}
