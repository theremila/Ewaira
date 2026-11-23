package remila.sex.cirrus.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalLongRef;
import remila.sex.cirrus.culling.EntityOcclusionEngine;
import remila.sex.cirrus.culling.SectionOccupancyAccess;
import remila.sex.cirrus.culling.SectionOccupancyMap;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionCompiler.class)
abstract class SectionOccupancyPublishMixin {
    @Inject(method = "compile", at = @At("HEAD"))
    private void cirrus$captureWorldEpoch(CallbackInfoReturnable<SectionCompiler.Results> cir,
                                          @Share("worldEpoch") LocalLongRef worldEpoch) {
        worldEpoch.set(SectionOccupancyMap.worldEpoch());
    }

    @Inject(method = "compile", at = @At("RETURN"))
    private void cirrus$publishOccupancy(CallbackInfoReturnable<SectionCompiler.Results> cir,
                                         @Local(argsOnly = true) SectionPos sectionPos,
                                         @Share("worldEpoch") LocalLongRef worldEpoch) {
        if (!EntityOcclusionEngine.isEnabled()) return;
        SectionCompiler.Results results = cir.getReturnValue();
        if (results == null) return;
        long[] mask = results.visibilitySet instanceof SectionOccupancyAccess access ? access.cirrus$occupancy() : null;
        SectionOccupancyMap.putIfWorldEpoch(worldEpoch.get(), sectionPos.asLong(), mask);
    }
}
