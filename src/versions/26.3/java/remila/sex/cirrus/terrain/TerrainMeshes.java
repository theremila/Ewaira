package remila.sex.cirrus.terrain;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import remila.sex.cirrus.lod.TerrainLod;
import org.lwjgl.system.MemoryUtil;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;

public final class TerrainMeshes {
    private TerrainMeshes() {}
    public static TerrainFaces sort(MeshData mesh) {
        if ((!TerrainOptimization.isEnabled() && !TerrainLod.isEnabled()) || mesh == null || mesh.indexBuffer() != null
                || mesh.drawState().format() != DefaultVertexFormat.BLOCK || !(mesh.drawState().primitiveTopology() == PrimitiveTopology.QUADS)) return null;
        var state = mesh.drawState();
        int stride = state.format().getVertexSize();
        TerrainFaces faces = TerrainFaces.sort(mesh.vertexBuffer(), stride, state.vertexCount());
        if (faces == null || !TerrainLod.isEnabled()) return faces;
        TerrainLodIndices.Result lod = TerrainLodIndices.buildOffHeap(mesh.vertexBuffer(), stride, state.vertexCount());
        if (lod == null) return faces;
        int indexBytes = lod.count() * Short.BYTES;
        ByteBufferBuilder arena = new ByteBufferBuilder(indexBytes);
        try {
            lod.copyTo(arena.reserve(indexBytes));
            ((TerrainIndexAccess) mesh).cirrus$indices(arena,
                    new MeshData.DrawState(state.format(), state.vertexCount(), lod.count(), state.primitiveTopology(), IndexType.SHORT));
        } catch (RuntimeException | Error failure) {
            arena.close();
            throw failure;
        }
        faces.attachLod(lod);
        return faces;
    }
}
