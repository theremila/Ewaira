package remila.sex.cirrus.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    @Accessor("viewArea") ViewArea cirrus$getViewArea();
    @Accessor("cullingFrustum") Frustum cirrus$getCullingFrustum();
    @Accessor("capturedFrustum") Frustum cirrus$getCapturedFrustum();
}
