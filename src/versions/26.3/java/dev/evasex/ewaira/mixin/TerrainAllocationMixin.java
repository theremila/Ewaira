package dev.evasex.ewaira.mixin;

import com.mojang.blaze3d.vertex.UberGpuBuffer;
import dev.evasex.ewaira.terrain.ChunkDrawPreparation;
import dev.evasex.ewaira.terrain.TerrainSliceAccess;
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
    private void ewaira$invalidateBinding(Object key, CallbackInfo ci) {
        if (key instanceof TerrainSliceAccess access) {
            ChunkDrawPreparation.invalidateAllocations();
            access.ewaira$sectionCache().clear();
        }
    }
    @Inject(method = "close", at = @At("HEAD"))
    private void ewaira$invalidateAll(CallbackInfo ci) {
        if (!allocationMap.isEmpty()) ChunkDrawPreparation.invalidateAllocations();
        for (Object key : allocationMap.keySet())
            if (key instanceof TerrainSliceAccess access) access.ewaira$sectionCache().clear();
    }
}
