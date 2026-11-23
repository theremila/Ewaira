package remila.sex.cirrus.terrain;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;

/** Transfers the LOD index arena to the native mesh's close lifecycle. */
public interface TerrainIndexAccess {
    void cirrus$indices(ByteBufferBuilder arena, MeshData.DrawState state);
}
