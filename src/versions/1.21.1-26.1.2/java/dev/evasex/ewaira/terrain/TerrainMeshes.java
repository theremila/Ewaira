package dev.evasex.ewaira.terrain;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import dev.evasex.ewaira.lod.TerrainLod;
import org.lwjgl.system.MemoryUtil;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

public final class TerrainMeshes {
    private TerrainMeshes() {}
    public static TerrainFaces sort(MeshData mesh) {
        if ((!TerrainOptimization.isEnabled() && !TerrainLod.isEnabled()) || mesh == null || mesh.indexBuffer() != null
                || mesh.drawState().format() != DefaultVertexFormat.BLOCK || !(mesh.drawState().mode() == VertexFormat.Mode.QUADS)) return null;
        var state = mesh.drawState();
        int stride = state.format().getVertexSize();
        TerrainFaces faces = TerrainFaces.sort(mesh.vertexBuffer(), stride, state.vertexCount());
        if (faces == null || !TerrainLod.isEnabled()) return faces;
        TerrainLodIndices.Result lod = TerrainLodIndices.build(mesh.vertexBuffer(), stride, state.vertexCount());
        if (lod == null) return faces;
        int indexBytes = lod.count() * Short.BYTES;
        ByteBufferBuilder arena = new ByteBufferBuilder(indexBytes);
        try {
            MemoryUtil.memShortBuffer(arena.reserve(indexBytes), lod.count()).put(lod.indices(), 0, lod.count());
            ((TerrainIndexAccess) mesh).ewaira$indices(arena,
                    new MeshData.DrawState(state.format(), state.vertexCount(), lod.count(), state.mode(), VertexFormat.IndexType.SHORT));
        } catch (RuntimeException | Error failure) {
            arena.close();
            throw failure;
        }
        faces.attachLod(lod);
        return faces;
    }
}
