package remila.sex.cirrus.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import remila.sex.cirrus.worldgen.DensityBufferReuse;
import remila.sex.cirrus.worldgen.SurfaceDensityBuffers;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.material.MaterialSystem;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MaterialSystem.class)
abstract class SurfaceDensityScopeMixin {
    @WrapMethod(method = "buildSurface")
    private void cirrus$withSurfaceBuffers(RandomState random, BiomeManager biomes, WorldGenerationContext context,
            ChunkAccess chunk, NoiseChunk noise, MaterialRule rule, Set<Holder<Biome>> possibleBiomes, Operation<Void> vanilla) {
        if (!DensityBufferReuse.isEnabled()) {
            vanilla.call(random, biomes, context, chunk, noise, rule, possibleBiomes);
            return;
        }
        SurfaceDensityBuffers buffers = SurfaceDensityBuffers.worker();
        buffers.enterSurface();
        try {
            vanilla.call(random, biomes, context, chunk, noise, rule, possibleBiomes);
        } finally {
            buffers.leaveSurface();
        }
    }
}
