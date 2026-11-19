package dev.evasex.ewaira.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    @Accessor("viewArea") ViewArea ewaira$getViewArea();
    @Accessor("cullingFrustum") Frustum ewaira$getCullingFrustum();
    @Accessor("capturedFrustum") Frustum ewaira$getCapturedFrustum();
}
