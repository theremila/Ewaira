package dev.evasex.ewaira.terrain;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionMesh;

public interface TerrainDispatcherAccess {
    TerrainSectionCache.Entry ewaira$cachedSlice(SectionMesh mesh, TerrainSectionCache cache, ChunkSectionLayer layer);
}
