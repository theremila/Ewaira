package remila.sex.cirrus.mixin;

import remila.sex.cirrus.culling.EntityOcclusionEngine;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderDispatcher.class)
abstract class BlockEntityOcclusionMixin {
    @Shadow private Vec3 cameraPos;

    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity, S extends BlockEntityRenderState> void cirrus$cullOccludedBlockEntity(
            E blockEntity, float partialTick, ModelFeatureRenderer.CrumblingOverlay overlay, boolean alwaysVisible,
            CallbackInfoReturnable<S> cir) {
        if (alwaysVisible || cameraPos == null) return;
        if (EntityOcclusionEngine.isBlockEntityOccluded(blockEntity, cameraPos.x, cameraPos.y, cameraPos.z)) {
            cir.setReturnValue(null);
        }
    }
}
