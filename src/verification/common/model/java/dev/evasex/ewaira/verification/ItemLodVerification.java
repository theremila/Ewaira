package dev.evasex.ewaira.verification;

import dev.evasex.ewaira.lod.ItemLod;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sun.misc.Unsafe;

/** Executes transformed native item renderer hooks without starting graphics. */
public final class ItemLodVerification {
    private ItemLodVerification() {}
    public static String verify() throws ReflectiveOperationException {
        Class<?> renderer = Class.forName("net.minecraft.client.renderer.entity.ItemEntityRenderer");
        ItemLod.setEnabled(true); ItemLod.setDistance(32);
        try {
            Class<?> state;
            try { state = Class.forName("net.minecraft.client.renderer.entity.state.ItemEntityRenderState"); }
            catch (ClassNotFoundException legacy) { return verifyLegacy(renderer); }
            Object snapshot = state.getConstructor().newInstance();
            Method hook = Arrays.stream(renderer.getDeclaredMethods()).filter(m -> m.getName().contains("ewaira$itemLod") && m.getParameterCount() == 4).findFirst().orElseThrow();
            hook.setAccessible(true);
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
            Object instance = ((Unsafe) unsafeField.get(null)).allocateInstance(renderer);
            for (double distance : new double[]{0, 255, 256, 1023, 1024, 10000}) {
                state.getField("distanceToCameraSq").setDouble(snapshot, distance);
                state.getField("count").setInt(snapshot, 5);
                hook.invoke(instance, null, snapshot, 0f, new CallbackInfo("extractRenderState", false));
                int expected = distance >= 1024 ? 1 : distance >= 256 ? 2 : 5;
                if (state.getField("count").getInt(snapshot) != expected) throw new AssertionError("Native item LOD threshold mismatch");
            }
            ItemLod.setEnabled(false);
            state.getField("count").setInt(snapshot, 5);
            hook.invoke(instance, null, snapshot, 0f, new CallbackInfo("extractRenderState", false));
            if (state.getField("count").getInt(snapshot) != 5) throw new AssertionError("Disabled native item LOD changed count");
            return "PASS: native item LOD render-state hook: near=5, middle=2, far=1; disabled=5.\n";
        } finally { ItemLod.setEnabled(true); ItemLod.setDistance(32); }
    }
    private static String verifyLegacy(Class<?> renderer) throws ReflectiveOperationException {
        Method count = Arrays.stream(renderer.getDeclaredMethods()).filter(m -> m.getName().equals("getRenderedAmount")).findFirst().orElseThrow();
        count.setAccessible(true);
        if ((int) count.invoke(null, 64) != 5) throw new AssertionError("Unscoped item renderer changed");
        double previous = ItemLod.enter(1024);
        try {
            if ((int) count.invoke(null, 64) != 1) throw new AssertionError("Native far stack did not collapse");
            ItemLod.setEnabled(false);
            if ((int) count.invoke(null, 64) != 5) throw new AssertionError("Disabled legacy LOD changed count");
        } finally { ItemLod.restore(previous); ItemLod.setEnabled(true); }
        if ((int) count.invoke(null, 64) != 5) throw new AssertionError("Legacy scope leaked");
        return "PASS: native item LOD stack hook: far=1, disabled=5; non-world helpers and restored scope=5.\n";
    }
}
