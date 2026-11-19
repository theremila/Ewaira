package dev.evasex.ewaira.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.evasex.ewaira.terrain.TerrainFaces;
import dev.evasex.ewaira.terrain.TerrainMeshAccess;
import dev.evasex.ewaira.terrain.TerrainOptimization;
import dev.evasex.ewaira.terrain.TerrainVertexBufferAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VertexBuffer.class)
abstract class TerrainVertexBufferMixin implements TerrainVertexBufferAccess {
    @Shadow private VertexFormat.Mode mode;
    @Shadow private int indexCount;
    @Shadow private RenderSystem.AutoStorageIndexBuffer sequentialIndices;
    @Shadow private VertexFormat.IndexType getIndexType() { throw new AssertionError(); }
    @Unique private TerrainFaces ewaira$faces;

    @Inject(method = "upload", at = @At("RETURN"))
    private void ewaira$receiveFaces(MeshData mesh, CallbackInfo ci) {
        ewaira$faces = ((TerrainMeshAccess) mesh).ewaira$terrainFaces();
    }
    @Inject(method = {"uploadIndexBuffer", "close"}, at = @At("HEAD"))
    private void ewaira$invalidateFaces(CallbackInfo ci) { ewaira$faces = null; }

    @Override public boolean ewaira$drawTerrain(double x, double y, double z) {
        if (ewaira$faces == null || mode != VertexFormat.Mode.QUADS
                || (sequentialIndices == null && !ewaira$faces.hasLod())) return false;
        RenderSystem.assertOnRenderThread();
        long range = ewaira$faces.range(indexCount, x, y, z);
        VertexFormat.IndexType type = getIndexType();
        GlStateManager._drawElements(mode.asGLMode, TerrainFaces.indexCount(range), type.asGLType,
                (long) TerrainFaces.firstIndex(range) * type.bytes);
        return true;
    }
}
