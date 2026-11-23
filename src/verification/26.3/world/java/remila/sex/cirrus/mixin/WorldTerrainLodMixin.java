package remila.sex.cirrus.mixin;

import remila.sex.cirrus.lod.TerrainLod;
import remila.sex.cirrus.terrain.TerrainFaces;
import remila.sex.cirrus.terrain.TerrainLodIndices;
import remila.sex.cirrus.verification.TerrainLodWorldStats;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TerrainFaces.class)
abstract class WorldTerrainLodMixin {
    @Inject(method = "attachLod", at = @At("RETURN"))
    private void cirrus$compiledLod(TerrainLodIndices.Result indices, CallbackInfo ci) {
        TerrainLodWorldStats.compiled.incrementAndGet();
        TerrainLodWorldStats.fullIndices.addAndGet(indices.middleStart());
        TerrainLodWorldStats.middleIndices.addAndGet(indices.farStart() - indices.middleStart());
        TerrainLodWorldStats.farIndices.addAndGet(indices.count() - indices.farStart());
    }
    @ModifyReturnValue(method = "range(IDDDZ)J", at = @At("RETURN"))
    private long cirrus$lodDraw(long range, int count, double x, double y, double z, boolean cull) {
        if (TerrainLodWorldStats.recordDraws && ((TerrainFaces) (Object) this).hasLod() && TerrainFaces.indexCount(range) > 0) {
            TerrainLodWorldStats.draws[TerrainLod.level(x, y, z)].incrementAndGet();
        }
        return range;
    }
}
