package remila.sex.cirrus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderPass;
import remila.sex.cirrus.terrain.TerrainFaces;
import remila.sex.cirrus.terrain.TerrainMeshAccess;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
abstract class TerrainDrawMixin {
    @Shadow @Final private LevelRenderState levelRenderState;
    @ModifyExpressionValue(method = "prepareChunkRenders", at = @At(value = "NEW",
            target = "com/mojang/blaze3d/systems/RenderPass$Draw"))
    private RenderPass.Draw<?> cirrus$trimSolid(RenderPass.Draw<?> draw, @Local SectionMesh mesh, @Local ChunkSectionLayer layer,
            @Local BlockPos origin) {
        if (layer != ChunkSectionLayer.SOLID || !(mesh instanceof TerrainMeshAccess access)) return draw;
        TerrainFaces faces = access.cirrus$terrainFaces();
        if (faces == null || (draw.indexBuffer() != null && !faces.hasLod())) return draw;
        long range;
        if (levelRenderState.cameraRenderState == null) {
            range = faces.fullRange();
        } else {
            Vec3 eye = levelRenderState.cameraRenderState.pos;
            range = faces.range(draw.indexCount(), eye.x - origin.getX(), eye.y - origin.getY(), eye.z - origin.getZ(), layer.pipeline().isCull());
        }
        int first = draw.firstIndex() + TerrainFaces.firstIndex(range);
        int count = TerrainFaces.indexCount(range);
        if (first == draw.firstIndex() && count == draw.indexCount()) return draw;
        return new RenderPass.Draw<>(draw.slot(), draw.vertexBuffer(), draw.indexBuffer(), draw.indexType(),
                first, count, draw.baseVertex(), draw.uniformUploaderConsumer());
    }
}
