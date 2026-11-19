package dev.evasex.ewaira.mixin;

import dev.evasex.ewaira.worldgen.BiomeSampling;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BiomeManager.class)
abstract class BiomeSamplingMixin {
    @Shadow @Final private long biomeZoomSeed;
    @Shadow @Final private BiomeResolver noiseBiomeSource;
    @Inject(method = "getBiome(III)Lnet/minecraft/core/Holder;", at = @At("HEAD"), cancellable = true)
    private void ewaira$biome(int x, int y, int z, CallbackInfoReturnable<Holder<Biome>> ci) {
        if (!BiomeSampling.isEnabled()) return;
        int corner = BiomeSampling.corner(biomeZoomSeed, x, y, z);
        ci.setReturnValue(noiseBiomeSource.getNoiseBiome(((x - 2) >> 2) + ((corner >> 2) & 1),
                ((y - 2) >> 2) + ((corner >> 1) & 1),
                ((z - 2) >> 2) + (corner & 1)));
    }
}
