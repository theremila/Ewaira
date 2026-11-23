package remila.sex.cirrus.mixin;

import remila.sex.cirrus.terrain.TerrainDispatcherAccess;
import remila.sex.cirrus.terrain.TerrainSectionCache;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SectionRenderDispatcher.class)
abstract class TerrainSliceCacheMixin implements TerrainDispatcherAccess {
    @Unique private final Object cirrus$bufferOwner = new Object();

    @Override public TerrainSectionCache.Entry cirrus$cachedSlice(SectionMesh mesh, TerrainSectionCache cache, ChunkSectionLayer layer) {
        TerrainSectionCache.Entry entry = cache.get(cirrus$bufferOwner, layer.ordinal());
        if (entry != null) return entry;
        SectionMesh.SectionDraw draw = mesh.getSectionDraw(layer);
        if (draw == null) return cache.rememberAbsent(layer);
        var slice = ((SectionRenderDispatcher) (Object) this).getRenderSectionSlice(mesh, layer);
        if (slice == null || (draw.hasCustomIndexBuffer() && slice.indexBuffer() == null)) return null;
        return cache.bind(layer, draw, slice);
    }
}
