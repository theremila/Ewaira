package remila.sex.cirrus.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import remila.sex.cirrus.terrain.TerrainFaces;
import remila.sex.cirrus.terrain.TerrainMeshAccess;
import remila.sex.cirrus.terrain.TerrainVertexBufferAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VertexBuffer.class)
abstract class TerrainVertexBufferMixin implements TerrainVertexBufferAccess {
    @Unique private static final int MIN_SAVED_INDICES_TO_SPLIT = 96; // 16 quads
    @Shadow private VertexFormat.Mode mode;
    @Shadow private int indexCount;
    @Shadow private RenderSystem.AutoStorageIndexBuffer sequentialIndices;
    @Shadow private VertexFormat.IndexType getIndexType() { throw new AssertionError(); }
    @Unique private TerrainFaces cirrus$faces;
    @Unique private final long[] cirrus$ranges = new long[TerrainFaces.MAX_RANGES];

    @Inject(method = "upload", at = @At("RETURN"))
    private void cirrus$receiveFaces(MeshData mesh, CallbackInfo ci) {
        cirrus$faces = ((TerrainMeshAccess) mesh).cirrus$terrainFaces();
    }
    @Inject(method = {"uploadIndexBuffer", "close"}, at = @At("HEAD"))
    private void cirrus$invalidateFaces(CallbackInfo ci) { cirrus$faces = null; }

    @Override public boolean cirrus$drawTerrain(double x, double y, double z) {
        if (cirrus$faces == null || mode != VertexFormat.Mode.QUADS
                || (sequentialIndices == null && !cirrus$faces.hasLod())) return false;
        RenderSystem.assertOnRenderThread();
        int rangeCount = cirrus$faces.ranges(indexCount, x, y, z, true, cirrus$ranges);
        if (rangeCount == 0) return true;
        VertexFormat.IndexType type = getIndexType();
        if (rangeCount == 1) {
            long r = cirrus$ranges[0];
            GlStateManager._drawElements(mode.asGLMode, TerrainFaces.indexCount(r), type.asGLType,
                    (long) TerrainFaces.firstIndex(r) * type.bytes);
            return true;
        }
        int first = TerrainFaces.firstIndex(cirrus$ranges[0]);
        long lastRange = cirrus$ranges[rangeCount - 1];
        int enclosing = TerrainFaces.firstIndex(lastRange) + TerrainFaces.indexCount(lastRange) - first;
        int exact = 0;
        for (int i = 0; i < rangeCount; i++) exact += TerrainFaces.indexCount(cirrus$ranges[i]);
        if (enclosing - exact >= MIN_SAVED_INDICES_TO_SPLIT) {
            for (int i = 0; i < rangeCount; i++) {
                long r = cirrus$ranges[i];
                GlStateManager._drawElements(mode.asGLMode, TerrainFaces.indexCount(r), type.asGLType,
                        (long) TerrainFaces.firstIndex(r) * type.bytes);
            }
        } else {
            GlStateManager._drawElements(mode.asGLMode, enclosing, type.asGLType, (long) first * type.bytes);
        }
        return true;
    }
}
