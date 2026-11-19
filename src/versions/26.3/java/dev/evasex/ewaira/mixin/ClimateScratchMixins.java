package dev.evasex.ewaira.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.evasex.ewaira.worldgen.BiomeSampling;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import dev.evasex.ewaira.worldgen.ClimateScratch;

public final class ClimateScratchMixins {
    private ClimateScratchMixins() {}

    /** The vanilla metric only reads the parameters synchronously. Custom searches keep the native array. */
    @Mixin(Climate.ParameterList.class)
    public abstract static class VanillaSearch {
        @WrapOperation(method = "findValueIndex(Lnet/minecraft/world/level/biome/Climate$TargetPoint;)Ljava/lang/Object;",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Climate$ParameterList;findValueIndex(Lnet/minecraft/world/level/biome/Climate$TargetPoint;Lnet/minecraft/world/level/biome/Climate$DistanceMetric;)Ljava/lang/Object;"))
        private Object ewaira$vanillaQuery(Climate.ParameterList<?> list, Climate.TargetPoint point,
                Climate.DistanceMetric<?> metric, Operation<Object> search) {
            if (!BiomeSampling.isEnabled() || list.getClass() != Climate.ParameterList.class)
                return search.call(list, point, metric);
            ClimateScratch scratch = ClimateScratch.current();
            if (scratch.point != null) return search.call(list, point, metric);
            scratch.point = point;
            scratch.metric = metric;
            scratch.taken = false;
            try {
                return search.call(list, point, metric);
            } finally {
                scratch.metric = null;
                scratch.point = null;
            }
        }
    }

    @Mixin(targets = "net.minecraft.world.level.biome.Climate$RTree")
    public abstract static class ParameterArray {
        @WrapOperation(method = "search", at = @At(value = "INVOKE",
                target = "Lnet/minecraft/world/level/biome/Climate$TargetPoint;toParameterArray()[J"))
        private long[] ewaira$parameterArray(Climate.TargetPoint point, Operation<long[]> allocate,
                @Local(argsOnly = true) Climate.DistanceMetric<?> metric) {
            if (!BiomeSampling.isEnabled()) return allocate.call(point);
            ClimateScratch scratch = ClimateScratch.current();
            if (scratch.point != point || scratch.metric != metric || scratch.taken) return allocate.call(point);
            scratch.taken = true;
            long[] parameters = scratch.parameters;
            parameters[0] = point.temperature();
            parameters[1] = point.humidity();
            parameters[2] = point.continentalness();
            parameters[3] = point.erosion();
            parameters[4] = point.depth();
            parameters[5] = point.weirdness();
            return parameters;
        }
    }
}
