package remila.sex.cirrus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import remila.sex.cirrus.worldgen.DensityBufferReuse;
import remila.sex.cirrus.worldgen.SurfaceDensityBuffers;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.MaterialRules.DensityGetter;
import net.minecraft.world.level.levelgen.material.rule.OreVeinRule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(OreVeinRule.class)
abstract class OreDensityScopeMixin {
    @WrapOperation(method = "compile", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/material/MaterialRuleContext;getDensitiesInChunk(Lnet/minecraft/world/level/levelgen/densityfunction/DensityFunction;Z)Lnet/minecraft/world/level/levelgen/material/MaterialRules$DensityGetter;"))
    private DensityGetter cirrus$scopedOreDensity(MaterialRuleContext context, DensityFunction density,
            boolean volume, Operation<DensityGetter> vanilla) {
        if (!volume || !DensityBufferReuse.isEnabled()) return vanilla.call(context, density, volume);
        SurfaceDensityBuffers buffers = SurfaceDensityBuffers.worker();
        if (!buffers.canEnterOre()) return vanilla.call(context, density, volume);
        buffers.enterOre();
        try {
            return vanilla.call(context, density, volume);
        } finally {
            buffers.leaveOre();
        }
    }
}
