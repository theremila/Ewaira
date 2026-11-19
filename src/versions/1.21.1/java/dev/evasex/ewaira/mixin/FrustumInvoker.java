package dev.evasex.ewaira.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Frustum.class)
public interface FrustumInvoker {
    // Use vanilla's test without allocating an AABB per particle per frame.
    @Invoker("cubeInFrustum")
    boolean ewaira$containsBox(double minX, double minY, double minZ,
                                  double maxX, double maxY, double maxZ);
}
