package dev.evasex.ewaira.terrain;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;

/** Transfers the LOD index arena to the native mesh's close lifecycle. */
public interface TerrainIndexAccess {
    void ewaira$indices(ByteBufferBuilder arena, MeshData.DrawState state);
}
