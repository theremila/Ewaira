package remila.sex.cirrus.mixin;

import remila.sex.cirrus.verification.OcclusionVerification;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Supplies fixture states only during the headless compile check. */
@Mixin(RenderChunkRegion.class)
abstract class OcclusionRegionVerificationMixin {
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void cirrus$fixtureState(BlockPos pos, CallbackInfoReturnable<BlockState> ci) {
        if (OcclusionVerification.active) ci.setReturnValue(OcclusionVerification.state(pos));
    }
}
