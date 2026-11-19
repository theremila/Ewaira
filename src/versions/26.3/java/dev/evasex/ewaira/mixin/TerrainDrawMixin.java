package dev.evasex.ewaira.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.DynamicGpuData;
import dev.evasex.ewaira.terrain.TerrainFaces;
import dev.evasex.ewaira.terrain.TerrainMeshAccess;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
abstract class TerrainDrawMixin {
    @Shadow @Final private LevelRenderState levelRenderState;
    @ModifyExpressionValue(method = "extractSectionDrawGroups", at = @At(value = "NEW",
            target = "net/minecraft/client/renderer/DynamicGpuData$IndexedDraw"))
    private DynamicGpuData.IndexedDraw ewaira$trimSolid(DynamicGpuData.IndexedDraw draw, @Local SectionMesh mesh, @Local ChunkSectionLayer layer,
            @Local BlockPos origin) {
        if (layer != ChunkSectionLayer.SOLID || !(mesh instanceof TerrainMeshAccess access)) return draw;
        TerrainFaces faces = access.ewaira$terrainFaces();
        if (faces == null || (mesh.getSectionDraw(layer).hasCustomIndexBuffer() && !faces.hasLod())) return draw;
        long range;
        if (levelRenderState.cameraRenderState == null || levelRenderState.renderWireframeTerrain) {
            range = faces.fullRange();
        } else {
            Vec3 eye = levelRenderState.cameraRenderState.pos;
            range = faces.range(draw.indexCount(), eye.x - origin.getX(), eye.y - origin.getY(), eye.z - origin.getZ(), layer.pipeline(false).isCull() && layer.pipeline(true).isCull());
        }
        int first = draw.firstIndex() + TerrainFaces.firstIndex(range);
        int count = TerrainFaces.indexCount(range);
        if (first == draw.firstIndex() && count == draw.indexCount()) return draw;
        return new DynamicGpuData.IndexedDraw(count, draw.instanceCount(), first, draw.baseVertex(), draw.baseInstance());
    }
}
