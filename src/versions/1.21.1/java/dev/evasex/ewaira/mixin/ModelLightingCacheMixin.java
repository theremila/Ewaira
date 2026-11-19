package dev.evasex.ewaira.mixin;

import dev.evasex.ewaira.mesh.ChunkLighting;
import dev.evasex.ewaira.mesh.SectionLightingCache;
import it.unimi.dsi.fastutil.longs.Long2FloatLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
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
            ewaira$region = region;
        }
        return ewaira$lighting;
    }

    /**
     * @author evasex
     * @reason Keep the primitive hot path allocation-free; retain vanilla's fallback when switched off.
     */
    @Overwrite
    public int getLightColor(BlockState state, BlockAndTintGetter region, BlockPos pos) {
        long key = pos.asLong();
        if (enabled && ChunkLighting.isEnabled()) {
            SectionLightingCache cache = ewaira$cache(region);
            int light = cache.getLight(key);
            if (light != SectionLightingCache.LIGHT_MISS) return light;
            light = LevelRenderer.getLightColor(region, state, pos);
            cache.putLight(key, light);
            return light;
        }
        if (enabled) {
            int light = colorCache.get(key);
            if (light != Integer.MAX_VALUE) return light;
        }
        int light = LevelRenderer.getLightColor(region, state, pos);
        if (enabled) {
            if (colorCache.size() == 100) colorCache.removeFirstInt();
            colorCache.put(key, light);
        }
        return light;
    }

    /**
     * @author evasex
     * @reason Preserve vanilla shade calculations while caching the whole active section's working set.
     */
    @Overwrite
    public float getShadeBrightness(BlockState state, BlockAndTintGetter region, BlockPos pos) {
        long key = pos.asLong();
        if (enabled && ChunkLighting.isEnabled()) {
            SectionLightingCache cache = ewaira$cache(region);
            float shade = cache.getShade(key);
            if (!Float.isNaN(shade)) return shade;
            shade = state.getShadeBrightness(region, pos);
            cache.putShade(key, shade);
            return shade;
        }
        if (enabled) {
            float shade = brightnessCache.get(key);
            if (!Float.isNaN(shade)) return shade;
        }
        float shade = state.getShadeBrightness(region, pos);
        if (enabled) {
            if (brightnessCache.size() == 100) brightnessCache.removeFirstFloat();
            brightnessCache.put(key, shade);
        }
        return shade;
    }
}
