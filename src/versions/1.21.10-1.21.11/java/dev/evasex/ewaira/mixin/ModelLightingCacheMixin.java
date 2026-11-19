package dev.evasex.ewaira.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.evasex.ewaira.mesh.ChunkLighting;
import dev.evasex.ewaira.mesh.SectionLightingCache;
import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2FloatLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.block.ModelBlockRenderer$Cache")
abstract class ModelLightingCacheMixin {
    @Shadow private boolean enabled;
    @Shadow @Final private Long2IntLinkedOpenHashMap colorCache;
    @Shadow @Final private Long2FloatLinkedOpenHashMap brightnessCache;
    @Unique private SectionLightingCache ewaira$lighting;
    @Unique private BlockAndTintGetter ewaira$region;

    @Inject(method = {"enable", "disable"}, at = @At("HEAD"))
    private void ewaira$resetSection(CallbackInfo ci) {
        if (ewaira$lighting != null) ewaira$lighting.clear();
        ewaira$region = null;
    }

    @Unique
    private SectionLightingCache ewaira$cache(BlockAndTintGetter region) {
        if (ewaira$lighting == null) ewaira$lighting = new SectionLightingCache();
        if (ewaira$region != region) {
            ewaira$lighting.clear();
            // Vanilla's nested brightness getter also owns position-keyed cached values.
            colorCache.clear();
            brightnessCache.clear();
            ewaira$region = region;
        }
        return ewaira$lighting;
    }

    @WrapMethod(method = "getLightColor")
    private int ewaira$light(BlockState state, BlockAndTintGetter region, BlockPos pos, Operation<Integer> original) {
        if (!enabled || !ChunkLighting.isEnabled()) return original.call(state, region, pos);
        SectionLightingCache cache = ewaira$cache(region);
        long key = pos.asLong();
        int value = cache.getLight(key);
        if (value != SectionLightingCache.LIGHT_MISS) return value;
        value = original.call(state, region, pos);
        cache.putLight(key, value);
        return value;
    }

    @WrapMethod(method = "getShadeBrightness")
    private float ewaira$shade(BlockState state, BlockAndTintGetter region, BlockPos pos, Operation<Float> original) {
        if (!enabled || !ChunkLighting.isEnabled()) return original.call(state, region, pos);
        SectionLightingCache cache = ewaira$cache(region);
        long key = pos.asLong();
        float value = cache.getShade(key);
        if (!Float.isNaN(value)) return value;
        value = original.call(state, region, pos);
        cache.putShade(key, value);
        return value;
    }
}
