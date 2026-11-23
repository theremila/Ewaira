package remila.sex.cirrus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import remila.sex.cirrus.worldgen.DensityBufferReuse;
import remila.sex.cirrus.worldgen.SurfaceDensityBuffers;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MaterialRuleContext.class)
abstract class OreDensityBufferMixin {
    @WrapOperation(method = "getDensitiesInChunk", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/densityfunction/DensityBuffer;createUnpooled(I)Lnet/minecraft/world/level/levelgen/densityfunction/DensityBuffer;"))
    private DensityBuffer cirrus$reuseOreVolume(int size, Operation<DensityBuffer> vanilla) {
        if (!DensityBufferReuse.isEnabled()) return vanilla.call(size);
        DensityBuffer buffer = SurfaceDensityBuffers.worker().acquire(size);
        return buffer == null ? vanilla.call(size) : buffer;
    }
}
