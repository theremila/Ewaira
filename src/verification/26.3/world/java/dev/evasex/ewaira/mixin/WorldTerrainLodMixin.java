package dev.evasex.ewaira.mixin;

import dev.evasex.ewaira.lod.TerrainLod;
import dev.evasex.ewaira.terrain.TerrainFaces;
import dev.evasex.ewaira.terrain.TerrainLodIndices;
import dev.evasex.ewaira.verification.TerrainLodWorldStats;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TerrainFaces.class)
abstract class WorldTerrainLodMixin {
    @Inject(method = "attachLod", at = @At("RETURN"))
    private void ewaira$compiledLod(TerrainLodIndices.Result indices, CallbackInfo ci) {
        TerrainLodWorldStats.compiled.incrementAndGet();
        TerrainLodWorldStats.fullIndices.addAndGet(indices.middleStart());
        TerrainLodWorldStats.middleIndices.addAndGet(indices.farStart() - indices.middleStart());
        TerrainLodWorldStats.farIndices.addAndGet(indices.count() - indices.farStart());
    }
    @ModifyReturnValue(method = "range(IDDDZ)J", at = @At("RETURN"))
    private long ewaira$lodDraw(long range, int count, double x, double y, double z, boolean cull) {
        if (TerrainLodWorldStats.recordDraws && ((TerrainFaces) (Object) this).hasLod() && TerrainFaces.indexCount(range) > 0) {
            TerrainLodWorldStats.draws[TerrainLod.level(x, y, z)].incrementAndGet();
        }
        return range;
    }
}
