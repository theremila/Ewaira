package dev.evasex.ewaira.mixin;

import dev.evasex.ewaira.terrain.TerrainDispatcherAccess;
import dev.evasex.ewaira.terrain.TerrainSectionCache;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SectionRenderDispatcher.class)
abstract class TerrainSliceCacheMixin implements TerrainDispatcherAccess {
    @Unique private final Object ewaira$bufferOwner = new Object();

    @Override public TerrainSectionCache.Entry ewaira$cachedSlice(SectionMesh mesh, TerrainSectionCache cache, ChunkSectionLayer layer) {
        TerrainSectionCache.Entry entry = cache.get(ewaira$bufferOwner, layer.ordinal());
        if (entry != null) return entry;
        SectionMesh.SectionDraw draw = mesh.getSectionDraw(layer);
        if (draw == null) return cache.rememberAbsent(layer);
        var slice = ((SectionRenderDispatcher) (Object) this).getRenderSectionSlice(mesh, layer);
        if (slice == null || (draw.hasCustomIndexBuffer() && slice.indexBuffer() == null)) return null;
        return cache.bind(layer, draw, slice);
    }
}
