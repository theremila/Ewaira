package remila.sex.cirrus.mixin;

import net.minecraft.client.renderer.DynamicGpuData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Pre-sizes chunk GPU ring buffers to avoid 16 cascading Vulkan buffer reallocations after world join. */
@Mixin(DynamicGpuData.class)
abstract class DynamicGpuCapacityMixin {
    @ModifyArg(method = "writeChunkSectionsInstanced", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/DynamicGpuDataStorageNonMapped;<init>(Ljava/lang/String;III)V"), index = 3)
    private int cirrus$initialInstancedCapacity(int original) {
        return Math.max(original, 4096);
    }

    @ModifyArg(method = "writeChunkSectionCommands", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/DynamicGpuDataStorageMapped;<init>(Ljava/lang/String;III)V"), index = 3)
    private int cirrus$initialCommandCapacity(int original) {
        return Math.max(original, 16384);
    }
}
