package remila.sex.cirrus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.commands.RenderPass;
import remila.sex.cirrus.verification.WorldOptimizationStats;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.material.MaterialSystem;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class WorldOptimizationMixins {
    private WorldOptimizationMixins() {}

    @Mixin(targets = "remila.sex.cirrus.terrain.PreparedDrawSnapshot", remap = false)
    public abstract static class Snapshot {
        @Inject(method = "matches", at = @At("HEAD"), cancellable = true)
        private void cirrus$selectReplay(CallbackInfoReturnable<Boolean> result) {
            if (!WorldOptimizationStats.replayAllowed) result.setReturnValue(false);
        }

        @Inject(method = "reserve", at = @At("HEAD"), cancellable = true)
        private void cirrus$skipSnapshotConstruction(CallbackInfoReturnable<Boolean> result) {
            if (!WorldOptimizationStats.replayAllowed) result.setReturnValue(false);
        }

        @Inject(method = "copyTo", at = @At("RETURN"))
        private void cirrus$replayed(CallbackInfoReturnable<Integer> result) {
            WorldOptimizationStats.drawGroupHits++;
        }
    }

    @Mixin(targets = "remila.sex.cirrus.worldgen.SurfaceDensityBuffers", remap = false)
    public abstract static class Density {
        @Inject(method = "acquire", at = @At("RETURN"))
        private void cirrus$leased(int size, CallbackInfoReturnable<DensityBuffer> result) {
            if (result.getReturnValue() != null) {
                WorldOptimizationStats.densityLeases.incrementAndGet();
                WorldOptimizationStats.densityCapacity.accumulateAndGet(size, Math::max);
            }
        }

        @WrapOperation(method = "acquire", at = @At(value = "INVOKE",
                target = "Lnet/minecraft/world/level/levelgen/densityfunction/DensityBuffer;createUnpooled(I)Lnet/minecraft/world/level/levelgen/densityfunction/DensityBuffer;"))
        private DensityBuffer cirrus$allocated(int size, Operation<DensityBuffer> allocate) {
            WorldOptimizationStats.densityAllocations.incrementAndGet();
            return allocate.call(size);
        }
    }

    @Mixin(MaterialSystem.class)
    public abstract static class Surface {
        @Inject(method = "buildSurface", at = @At("TAIL"))
        private void cirrus$surfaceBlocks(RandomState random, BiomeManager biomes, WorldGenerationContext context,
                ChunkAccess chunk, NoiseChunk noise, MaterialRule rule, Set<Holder<Biome>> possibleBiomes, CallbackInfo ci) {
            WorldOptimizationStats.recordSurface(chunk);
        }
    }

    @Mixin(targets = "remila.sex.cirrus.lod.FarTerrainRenderer", remap = false)
    public abstract static class FarTerrain {
        @WrapOperation(method = "draw", at = @At(value = "INVOKE",
                target = "Lcom/mojang/renderpearl/api/commands/RenderPass;draw(IIII)V"))
        private void cirrus$drew(RenderPass pass, int vertices, int instances, int firstVertex, int firstInstance,
                Operation<Void> draw) {
            draw.call(pass, vertices, instances, firstVertex, firstInstance);
            if (instances != 1 || vertices < 6 || vertices % 3 != 0) throw new AssertionError("Invalid distant terrain draw");
            WorldOptimizationStats.farTerrainDraws++;
            WorldOptimizationStats.farTerrainMaxVertices = Math.max(vertices, WorldOptimizationStats.farTerrainMaxVertices);
        }
    }
}
