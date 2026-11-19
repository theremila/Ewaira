package dev.evasex.ewaira.terrain;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.IndexType;
import java.util.Arrays;
import net.minecraft.client.renderer.DynamicGpuData.ChunkSectionInfo;
import net.minecraft.client.renderer.DynamicGpuData.IndexedDraw;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionMesh.SectionDraw;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSectionBufferSlice;

/** Owned by one compiled mesh; native allocation changes invalidate its bindings. */
public final class TerrainSectionCache {
    public static final Entry ABSENT = new Entry(null, null, null, 0, 0, 0);
    private static final int LAYER_COUNT = ChunkSectionLayer.values().length;
    private Object dispatcher;
    private final Entry[] entries = new Entry[LAYER_COUNT];
    ChunkSectionInfo info;
    long revision;

    public Entry get(Object owner, int layer) {
        if (dispatcher != owner) { clear(); dispatcher = owner; }
        return entries[layer];
    }
    public Entry rememberAbsent(ChunkSectionLayer layer) {
        return entries[layer.ordinal()] = ABSENT;
    }

    public Entry bind(ChunkSectionLayer layer, SectionDraw draw, RenderSectionBufferSlice slice) {
        IndexType type = draw.hasCustomIndexBuffer() ? draw.indexType() : null;
        GpuBuffer index = type == null ? null : slice.indexBuffer();
        int first = type == null ? 0 : (int) (slice.indexBufferOffset() / type.bytes);
        int base = (int) (slice.vertexBufferOffset() / layer.vertexFormat().getVertexSize());
        return entries[layer.ordinal()] = new Entry(slice.vertexBuffer(), index, type, draw.indexCount(), first, base);
    }
    public void clear() {
        revision++;
        Arrays.fill(entries, null);
        info = null;
        dispatcher = null;
    }

    public static final class Entry {
        final GpuBuffer vertex, index;
        final IndexType type;
        final int count, first, base;
        Object groupOwner;
        ChunkDrawPreparation.Group group;
        IndexedDraw draw;
        private Entry(GpuBuffer vertex, GpuBuffer index, IndexType type, int count, int first, int base) {
            this.vertex = vertex;
            this.index = index;
            this.type = type;
            this.count = count;
            this.first = first;
            this.base = base;
        }
    }
}
