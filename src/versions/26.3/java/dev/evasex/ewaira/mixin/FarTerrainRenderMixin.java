package dev.evasex.ewaira.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import dev.evasex.ewaira.lod.FarTerrainRenderer;
import dev.evasex.ewaira.debug.RenderDiagnostics;
import dev.evasex.ewaira.debug.RenderDiagnostics.Metric;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class FarTerrainRenderMixin {
    @Shadow @Final private LevelRenderState levelRenderState;
    @Unique private final FarTerrainRenderer ewaira$distantTerrain = new FarTerrainRenderer();

    @Unique private long ewaira$detailUploadTime;

    @Inject(method = "render", at = @At("HEAD"))
    private void ewaira$prepareFarTerrain(GraphicsResourceAllocator allocator, boolean outline, CameraRenderState camera,
            GpuBufferSlice fog, Vector4f fogColor, boolean sky, boolean translucent, CallbackInfo ci) {
        ewaira$detailUploadTime = 0;
        ewaira$distantTerrain.prepare(Minecraft.getInstance(), camera);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;uploadTerrainBuffersToGpu()V"))
    private void ewaira$measureDetailUpload(SectionRenderDispatcher dispatcher, Operation<Void> upload) {
        boolean debug = RenderDiagnostics.enabled();
        if (!debug && !ewaira$distantTerrain.hasUpload()) {
            upload.call(dispatcher);
            return;
        }
        long started = System.nanoTime();
        upload.call(dispatcher);
        ewaira$detailUploadTime = System.nanoTime() - started;
        if (debug) RenderDiagnostics.add(Metric.DETAIL_UPLOAD_US, ewaira$detailUploadTime / 1000);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void ewaira$uploadFarTerrain(GraphicsResourceAllocator allocator, boolean outline, CameraRenderState camera,
            GpuBufferSlice fog, Vector4f fogColor, boolean sky, boolean translucent, CallbackInfo ci) {
        ewaira$distantTerrain.uploadAfterDetail(camera, ewaira$detailUploadTime);
    }

    @Inject(method = "executeSolid", at = @At("HEAD"))
    private void ewaira$renderFarTerrain(ChunkSectionsToRender chunks, FeatureRenderDispatcher.PreparedFrame features,
            RenderPass pass, CallbackInfo ci) {
        if (!levelRenderState.renderWireframeTerrain) ewaira$distantTerrain.draw(pass, levelRenderState.cameraRenderState);
    }

    @Inject(method = {"resetLevelRenderData", "close"}, at = @At("HEAD"))
    private void ewaira$releaseFarTerrain(CallbackInfo ci) {
        ewaira$distantTerrain.close();
    }
}
