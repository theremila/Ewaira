package remila.sex.cirrus.culling;

import remila.sex.cirrus.ModIdentity;

/** Additional bounded vanilla block-entity culling is installed only on 1.21.1. */
public final class BlockEntityCulling {
    private static volatile boolean enabled = !ModIdentity.disabledBySystemProperty();
    private BlockEntityCulling() {}
    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }
}
