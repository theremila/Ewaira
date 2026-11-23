package remila.sex.cirrus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexBuffer;
import remila.sex.cirrus.terrain.TerrainVertexBufferAccess;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
abstract class TerrainDrawMixin {
    @WrapOperation(method = "renderSectionLayer", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;draw()V"))
    private void cirrus$trimSolid(VertexBuffer buffer, Operation<Void> original,
            @Local(argsOnly = true) RenderType layer, @Local RenderSection section,
            @Local(argsOnly = true, ordinal = 0) double x, @Local(argsOnly = true, ordinal = 1) double y,
            @Local(argsOnly = true, ordinal = 2) double z) {
        BlockPos origin = section.getOrigin();
        if (layer == RenderType.solid() && ((TerrainVertexBufferAccess) buffer).cirrus$drawTerrain(
                x - origin.getX(), y - origin.getY(), z - origin.getZ())) return;
        original.call(buffer);
    }
}
