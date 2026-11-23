package remila.sex.cirrus.terrain;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionMesh;

public interface TerrainDispatcherAccess {
    TerrainSectionCache.Entry cirrus$cachedSlice(SectionMesh mesh, TerrainSectionCache cache, ChunkSectionLayer layer);
}
