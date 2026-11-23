package remila.sex.cirrus.worldgen;

import remila.sex.cirrus.ModIdentity;

public final class DensityBufferReuse {
    private static volatile boolean enabled = !ModIdentity.disabledBySystemProperty();

    private DensityBufferReuse() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }
}
