package remila.sex.cirrus.mixin;

import com.mojang.blaze3d.vertex.UberGpuBuffer;
import remila.sex.cirrus.terrain.ChunkDrawPreparation;
import remila.sex.cirrus.terrain.TerrainSliceAccess;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(UberGpuBuffer.class)
abstract class TerrainAllocationMixin {
    @Shadow @Final private Map<?, ?> allocationMap;

    @Inject(method = "freeAllocation", at = @At("HEAD"))
    private void cirrus$invalidateBinding(Object key, CallbackInfo ci) {
        if (key instanceof TerrainSliceAccess access) {
            ChunkDrawPreparation.invalidateAllocations();
            access.cirrus$sectionCache().clear();
        }
    }
    @Inject(method = "close", at = @At("HEAD"))
    private void cirrus$invalidateAll(CallbackInfo ci) {
        if (!allocationMap.isEmpty()) ChunkDrawPreparation.invalidateAllocations();
        for (Object key : allocationMap.keySet())
            if (key instanceof TerrainSliceAccess access) access.cirrus$sectionCache().clear();
    }
}
