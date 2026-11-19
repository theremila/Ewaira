package dev.evasex.ewaira.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderPass;
import dev.evasex.ewaira.terrain.TerrainFaces;
import dev.evasex.ewaira.terrain.TerrainMeshAccess;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
abstract class TerrainDrawMixin {
    @ModifyExpressionValue(method = "prepareChunkRenders", at = @At(value = "NEW",
            target = "com/mojang/blaze3d/systems/RenderPass$Draw"))
    private RenderPass.Draw<?> ewaira$trimSolid(RenderPass.Draw<?> draw, @Local SectionMesh mesh, @Local ChunkSectionLayer layer,
            @Local BlockPos origin,
            @Local(argsOnly = true, ordinal = 0) double x, @Local(argsOnly = true, ordinal = 1) double y,
            @Local(argsOnly = true, ordinal = 2) double z) {
        if (layer != ChunkSectionLayer.SOLID || !(mesh instanceof TerrainMeshAccess access)) return draw;
        TerrainFaces faces = access.ewaira$terrainFaces();
        if (faces == null || (draw.indexBuffer() != null && !faces.hasLod())) return draw;
        long range = faces.range(draw.indexCount(), x - origin.getX(), y - origin.getY(), z - origin.getZ(), layer.pipeline().isCull());
        int first = draw.firstIndex() + TerrainFaces.firstIndex(range);
        int count = TerrainFaces.indexCount(range);
        if (first == draw.firstIndex() && count == draw.indexCount()) return draw;
        return new RenderPass.Draw<>(draw.slot(), draw.vertexBuffer(), draw.indexBuffer(), draw.indexType(),
                first, count, draw.uniformUploaderConsumer());
    }
}
