package remila.sex.cirrus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import remila.sex.cirrus.lod.FarTerrainRenderer;
import remila.sex.cirrus.debug.RenderDiagnostics;
import remila.sex.cirrus.debug.RenderDiagnostics.Metric;
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
    @Unique private final FarTerrainRenderer cirrus$distantTerrain = new FarTerrainRenderer();

    @Unique private long cirrus$detailUploadTime;

    @Inject(method = "render", at = @At("HEAD"))
    private void cirrus$prepareFarTerrain(GraphicsResourceAllocator allocator, boolean outline, CameraRenderState camera,
            GpuBufferSlice fog, Vector4f fogColor, boolean sky, boolean translucent, CallbackInfo ci) {
        cirrus$detailUploadTime = 0;
        cirrus$distantTerrain.prepare(Minecraft.getInstance(), camera);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;uploadTerrainBuffersToGpu()V"))
    private void cirrus$measureDetailUpload(SectionRenderDispatcher dispatcher, Operation<Void> upload) {
        boolean debug = RenderDiagnostics.enabled();
        if (!debug && !cirrus$distantTerrain.hasUpload()) {
            upload.call(dispatcher);
            return;
        }
        long started = System.nanoTime();
        upload.call(dispatcher);
        cirrus$detailUploadTime = System.nanoTime() - started;
        if (debug) RenderDiagnostics.add(Metric.DETAIL_UPLOAD_US, cirrus$detailUploadTime / 1000);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void cirrus$uploadFarTerrain(GraphicsResourceAllocator allocator, boolean outline, CameraRenderState camera,
            GpuBufferSlice fog, Vector4f fogColor, boolean sky, boolean translucent, CallbackInfo ci) {
        cirrus$distantTerrain.uploadAfterDetail(camera, cirrus$detailUploadTime);
    }

    @Inject(method = "executeSolid", at = @At("HEAD"))
    private void cirrus$renderFarTerrain(ChunkSectionsToRender chunks, FeatureRenderDispatcher.PreparedFrame features,
            RenderPass pass, CallbackInfo ci) {
        if (!levelRenderState.renderWireframeTerrain) cirrus$distantTerrain.draw(pass, levelRenderState.cameraRenderState);
    }

    @Inject(method = {"resetLevelRenderData", "close"}, at = @At("HEAD"))
    private void cirrus$releaseFarTerrain(CallbackInfo ci) {
        cirrus$distantTerrain.close();
    }
}
