package dev.evasex.ewaira.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.evasex.ewaira.occlusion.EntityOcclusion;
import dev.evasex.ewaira.occlusion.SectionOccluders;
import dev.evasex.ewaira.occlusion.VisibilityOccluders;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionCompiler.class)
abstract class SectionCompilerMixin {
    @Inject(method = "compile", at = @At("HEAD"))
    private void ewaira$beginOccluders(SectionPos section, RenderChunkRegion region, VertexSorting sorting,
            SectionBufferBuilderPack buffers, CallbackInfoReturnable<SectionCompiler.Results> ci,
            @Share("occluders") LocalRef<SectionOccluders.Builder> snapshot) {
        if (EntityOcclusion.isEnabled()) snapshot.set(new SectionOccluders.Builder(section.x(), section.y(), section.z()));
    }
    @WrapOperation(method = "compile", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/VisGraph;setOpaque(Lnet/minecraft/core/BlockPos;)V"))
    private void ewaira$captureOccluder(VisGraph graph, BlockPos pos, Operation<Void> original,
            @Local BlockState state, @Share("occluders") LocalRef<SectionOccluders.Builder> snapshot) {
        original.call(graph, pos);
        if (snapshot.get() != null && state.getRenderShape() == RenderShape.MODEL && !state.hasBlockEntity()
                && ItemBlockRenderTypes.getChunkRenderType(state) == RenderType.solid()) {
            snapshot.get().mark(pos.getX(), pos.getY(), pos.getZ());
        }
    }
    @Inject(method = "compile", at = @At("RETURN"))
    private void ewaira$finishOccluders(SectionPos section, RenderChunkRegion region, VertexSorting sorting,
            SectionBufferBuilderPack buffers, CallbackInfoReturnable<SectionCompiler.Results> ci,
            @Share("occluders") LocalRef<SectionOccluders.Builder> snapshot) {
        if (snapshot.get() != null) ((VisibilityOccluders) ci.getReturnValue().visibilitySet)
                .ewaira$setOccluders(snapshot.get().build());
    }
}
