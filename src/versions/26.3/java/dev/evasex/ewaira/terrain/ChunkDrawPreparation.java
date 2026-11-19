package dev.evasex.ewaira.terrain;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.IndexType;
import dev.evasex.ewaira.lod.TerrainLod;
import dev.evasex.ewaira.debug.RenderDiagnostics;
import dev.evasex.ewaira.debug.RenderDiagnostics.Metric;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.renderer.DynamicGpuData.ChunkSectionInfo;
import net.minecraft.client.renderer.DynamicGpuData.IndexedDraw;
import net.minecraft.client.renderer.LevelRenderer.ChunkDrawGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

/** Render-thread command preparation. Buffer allocation, uploads and locking stay native. */
public final class ChunkDrawPreparation {
    private static final AtomicLong ALLOCATION_EPOCH = new AtomicLong();
    private static final ChunkSectionLayer[] LAYERS = ChunkSectionLayer.values();
    private final Map<ChunkSectionLayer, IdentityHashMap<GpuBuffer, List<Group>>> groups = new EnumMap<>(ChunkSectionLayer.class);
    private final List<Group> orderedGroups = new ArrayList<>();
    private Object groupOwner = new Object();
    private long frame;
    private int groupCount;
    private Vec3 previousEye;
    private int previousVisibleCount;
    private long groupEpoch = -1;
    private final PreparedDrawSnapshot snapshot = new PreparedDrawSnapshot(LAYERS);

    public static void invalidateAllocations() {
        ALLOCATION_EPOCH.incrementAndGet();
    }

    public ChunkDrawPreparation() {
        for (ChunkSectionLayer layer : LAYERS) groups.put(layer, new IdentityHashMap<>());
    }

    /** -1 requests native fallback for renderer-provided custom meshes. Inputs are fresh native lists. */
    public int extract(boolean ordered, List<ChunkSectionInfo> infos, Map<ChunkSectionLayer, List<ChunkDrawGroup>> output,
            ObjectArrayList<RenderSection> visible, SectionRenderDispatcher dispatcher,
            LevelRenderState state, OptionsRenderState options) {
        if (dispatcher == null) return 0;
        Vec3 eye = state.cameraRenderState == null ? null : state.cameraRenderState.pos;
        boolean wireframe = state.renderWireframeTerrain;
        long fade = Util.toMillis(options.chunkSectionFadeInTime);
        boolean cull = ChunkSectionLayer.SOLID.pipeline(false).isCull() && ChunkSectionLayer.SOLID.pipeline(true).isCull();
        boolean debug = RenderDiagnostics.enabled();
        long started = debug ? System.nanoTime() : 0;
        dispatcher.lock();
        try {
            long allocations = ALLOCATION_EPOCH.get();
            double lodDistance = TerrainLod.isEnabled() ? TerrainLod.distance() : 0.0;
            double margin = TerrainOptimization.isEnabled() ? TerrainOptimization.margin() : 0.0;
            var pass = new PreparedDrawSnapshot.Pass(dispatcher, eye, ordered, wireframe, cull, fade, allocations,
                    lodDistance, margin);
            boolean moved = !Objects.equals(eye, previousEye);
            previousEye = eye;
            if (snapshot.matches(pass, visible)) {
                if (debug) RenderDiagnostics.add(moved ? Metric.MOVING_REPLAYS : Metric.STATIONARY_REPLAYS, 1);
                return snapshot.copyTo(infos, output);
            }
            boolean repeatedSize = visible.size() == previousVisibleCount;
            previousVisibleCount = visible.size();
            snapshot.invalidate();
            return rebuild(infos, output, visible, pass, repeatedSize || !moved);
        } finally {
            dispatcher.unlock();
            if (debug) RenderDiagnostics.add(Metric.TERRAIN_PREPARE_US, (System.nanoTime() - started) / 1000);
        }
    }

    private int rebuild(List<ChunkSectionInfo> infos, Map<ChunkSectionLayer, List<ChunkDrawGroup>> output,
            ObjectArrayList<RenderSection> visible, PreparedDrawSnapshot.Pass pass, boolean reusable) {
        frame++;
        if (groupCount > 256) clearGroups();
        if (groupEpoch != pass.allocations()) {
            groupEpoch = pass.allocations();
            int before = groupCount;
            removeClosedGroups();
            if (before != groupCount) groupOwner = new Object();
        }
        int largest = 0, orderedIndex = 0;
        Group lastTransparent = null;
        long now = Util.getMillis();
        boolean stable = reusable && snapshot.reserve(visible.size());
        for (int s = 0; s < visible.size(); s++) {
            RenderSection section = visible.get(s);
            SectionMesh mesh = section.getSectionMesh();
            if (stable) snapshot.record(s, section, mesh);
            if (mesh == CompiledSectionMesh.EMPTY || mesh == CompiledSectionMesh.UNCOMPILED) continue;
            if (mesh.getClass() != CompiledSectionMesh.class || !(mesh instanceof TerrainSliceAccess access)) {
                snapshot.invalidate();
                infos.clear();
                for (List<ChunkDrawGroup> list : output.values()) list.clear();
                return -1;
            }
            BlockPos origin = section.getRenderOrigin();
            TerrainSectionCache cache = access.ewaira$sectionCache();
            int infoIndex = -1;
            for (ChunkSectionLayer layer : LAYERS) {
                TerrainSectionCache.Entry entry = ((TerrainDispatcherAccess) pass.dispatcher()).ewaira$cachedSlice(mesh, cache, layer);
                if (entry == null) {
                    stable = false;
                    continue;
                }
                if (entry == TerrainSectionCache.ABSENT) continue;
                int count = entry.count;
                IndexType type = entry.type;
                GpuBuffer index = entry.index;
                int first = entry.first;
                if (layer == ChunkSectionLayer.SOLID && mesh instanceof TerrainMeshAccess terrain) {
                    TerrainFaces faces = terrain.ewaira$terrainFaces();
                    if (faces != null && (type == null || faces.hasLod())) {
                        long range = PreparedDrawSnapshot.range(pass, faces, count, origin);
                        if (stable) snapshot.recordRange(s, faces, count, range);
                        first += TerrainFaces.firstIndex(range);
                        count = TerrainFaces.indexCount(range);
                    }
                }
                // Fully hidden solids need neither a command nor a section UBO when no other layer survives.
                if (count == 0) continue;
                if (type == null) largest = Math.max(largest, entry.count);
                if (infoIndex == -1) {
                    infoIndex = infos.size();
                    float visibility = section.getVisibility(now, pass.fade());
                    if (visibility != 1.0f) stable = false;
                    ChunkSectionInfo info = cache.info;
                    if (info == null || info.x() != origin.getX() || info.y() != origin.getY()
                            || info.z() != origin.getZ() || info.visibility() != visibility)
                        cache.info = info = new ChunkSectionInfo(origin.getX(), origin.getY(), origin.getZ(), visibility);
                    infos.add(info);
                }
                GpuBuffer vertex = entry.vertex;
                Group group;
                if (pass.ordered() && layer.translucent()) {
                    if (lastTransparent != null && lastTransparent.matches(vertex, index, type)) group = lastTransparent;
                    else {
                        if (orderedIndex == orderedGroups.size()) orderedGroups.add(new Group(vertex, index, type));
                        group = orderedGroups.get(orderedIndex);
                        if (!group.matches(vertex, index, type)) orderedGroups.set(orderedIndex, group = new Group(vertex, index, type));
                        orderedIndex++;
                        lastTransparent = group;
                    }
                } else {
                    if (entry.groupOwner != groupOwner) {
                        entry.group = group(layer, vertex, index, type);
                        entry.groupOwner = groupOwner;
                    }
                    group = entry.group;
                }
                if (group.frame != frame) {
                    group.frame = frame;
                    group.commands.beginFrame();
                    output.get(layer).add(group.nativeGroup);
                }
                int base = entry.base;
                IndexedDraw command = entry.draw;
                if (command == null || command.indexCount() != count || command.firstIndex() != first
                        || command.baseInstance() != infoIndex)
                    entry.draw = command = new IndexedDraw(count, 1, first, base, infoIndex);
                group.commands.append(command);
            }
            if (stable) snapshot.recordBinding(s, cache);
        }
        for (List<ChunkDrawGroup> list : output.values()) {
            for (ChunkDrawGroup group : list) ((TerrainDrawCommands) group.draws()).finishFrame();
        }
        // Old transparent groups can reference heaps retired since the previous camera ordering.
        if (orderedGroups.size() > orderedIndex) orderedGroups.subList(orderedIndex, orderedGroups.size()).clear();
        if (stable) snapshot.store(pass, largest, infos, output);
        else snapshot.invalidate();
        return largest;
    }

    private Group group(ChunkSectionLayer layer, GpuBuffer vertex, GpuBuffer index, IndexType type) {
        List<Group> candidates = groups.get(layer).computeIfAbsent(vertex, ignored -> new ArrayList<>(1));
        for (Group candidate : candidates) if (candidate.matches(vertex, index, type)) return candidate;
        Group group = new Group(vertex, index, type);
        candidates.add(group); groupCount++;
        return group;
    }

    private void removeClosedGroups() {
        for (var byBuffer : groups.values()) {
            var iterator = byBuffer.values().iterator();
            while (iterator.hasNext()) {
                var candidates = iterator.next();
                for (Group group : candidates) {
                    if (group.vertex.isClosed() || (group.index != null && group.index.isClosed())) {
                        groupCount -= candidates.size();
                        iterator.remove();
                        break;
                    }
                }
            }
        }
    }

    public void clear() {
        previousEye = null;
        previousVisibleCount = 0;
        snapshot.invalidate();
        clearGroups();
        orderedGroups.clear();
    }
    private void clearGroups() {
        for (var map : groups.values()) map.clear();
        groupOwner = new Object(); groupCount = 0;
    }

    static final class Group {
        final GpuBuffer vertex, index;
        final IndexType type;
        final TerrainDrawCommands commands = new TerrainDrawCommands();
        final ChunkDrawGroup nativeGroup;
        long frame;
        Group(GpuBuffer vertex, GpuBuffer index, IndexType type) {
            this.vertex = vertex; this.index = index; this.type = type;
            nativeGroup = new ChunkDrawGroup(vertex.slice(), index == null ? null : index.slice(), type, commands);
        }
        boolean matches(GpuBuffer vertex, GpuBuffer index, IndexType type) {
            return this.vertex == vertex && this.index == index && this.type == type;
        }
    }
}
