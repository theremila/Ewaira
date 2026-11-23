package remila.sex.cirrus.terrain;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.renderer.DynamicGpuData.ChunkSectionInfo;
import net.minecraft.client.renderer.LevelRenderer.ChunkDrawGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Reuses groups while their renderer-owned inputs still describe the same settled set of commands. */
final class PreparedDrawSnapshot {
    private static final int MAX_VISIBLE_SECTIONS = 32_768;

    record Pass(SectionRenderDispatcher dispatcher, Vec3 eye, boolean ordered, boolean wireframe, boolean cull,
            long fade, long allocations, double lodDistance, double margin) {
        boolean sameCommands(Pass other) {
            return dispatcher == other.dispatcher && ordered == other.ordered && wireframe == other.wireframe
                    && cull == other.cull && fade == other.fade
                    && lodDistance == other.lodDistance && margin == other.margin;
        }
    }

    private final ChunkSectionLayer[] layers;
    private final Map<ChunkSectionLayer, ObjectArrayList<ChunkDrawGroup>> groups = new EnumMap<>(ChunkSectionLayer.class);
    private final ObjectArrayList<ChunkSectionInfo> infos = new ObjectArrayList<>();
    private RenderSection[] sections = new RenderSection[0];
    private SectionMesh[] meshes = new SectionMesh[0];
    private long[] nodes = new long[0];
    private TerrainFaces[] faces = new TerrainFaces[0];
    private int[] indexCounts = new int[0];
    private int[] visibilityKeys = new int[0];
    private TerrainSectionCache[] bindings = new TerrainSectionCache[0];
    private long[] bindingRevisions = new long[0];
    private int count;
    private int largest;
    private Pass pass;

    PreparedDrawSnapshot(ChunkSectionLayer[] layers) {
        this.layers = layers;
        for (ChunkSectionLayer layer : layers) groups.put(layer, new ObjectArrayList<>());
    }

    boolean reserve(int count) {
        if (count == 0 || count > MAX_VISIBLE_SECTIONS) return false;
        if (count > sections.length) {
            int capacity = Math.min(MAX_VISIBLE_SECTIONS, Math.max(count, sections.length * 2));
            sections = new RenderSection[capacity];
            meshes = new SectionMesh[capacity];
            nodes = new long[capacity];
            faces = new TerrainFaces[capacity];
            indexCounts = new int[capacity];
            visibilityKeys = new int[capacity];
            bindings = new TerrainSectionCache[capacity];
            bindingRevisions = new long[capacity];
        }
        this.count = count;
        return true;
    }

    void record(int slot, RenderSection section, SectionMesh mesh) {
        sections[slot] = section;
        meshes[slot] = mesh;
        nodes[slot] = section.getSectionNode();
    }

    void recordBinding(int slot, TerrainSectionCache cache) {
        bindings[slot] = cache;
        bindingRevisions[slot] = cache.revision;
    }

    void recordVisibility(int slot, TerrainFaces terrain, int count, int key) {
        faces[slot] = terrain;
        indexCounts[slot] = count;
        visibilityKeys[slot] = key;
    }

    static int ranges(Pass pass, TerrainFaces faces, int count, BlockPos origin, long[] outRanges) {
        Vec3 eye = pass.eye();
        if (eye == null || pass.wireframe()) {
            long full = faces.fullRange();
            outRanges[0] = full;
            return TerrainFaces.indexCount(full) > 0 ? 1 : 0;
        }
        return faces.ranges(count, eye.x - origin.getX(), eye.y - origin.getY(), eye.z - origin.getZ(), pass.cull(), outRanges);
    }

    static int visibilityKey(Pass pass, TerrainFaces faces, int count, BlockPos origin) {
        Vec3 eye = pass.eye();
        if (eye == null || pass.wireframe()) return 0xFF;
        return faces.visibilityKey(count, eye.x - origin.getX(), eye.y - origin.getY(), eye.z - origin.getZ(), pass.cull());
    }

    void store(Pass pass, int largest, List<ChunkSectionInfo> infos, Map<ChunkSectionLayer, List<ChunkDrawGroup>> groups) {
        this.pass = pass;
        this.largest = largest;
        this.infos.addAll(infos);
        for (ChunkSectionLayer layer : layers) this.groups.get(layer).addAll(groups.get(layer));
    }

    boolean matches(Pass current, ObjectArrayList<RenderSection> visible) {
        if (pass == null || !current.sameCommands(pass) || count != visible.size()) return false;
        boolean moved = !Objects.equals(current.eye(), pass.eye());
        boolean allocationsChanged = current.allocations() != pass.allocations();
        for (int i = 0; i < count; i++) {
            RenderSection section = visible.get(i);
            if (section != sections[i] || section.getSectionMesh() != meshes[i] || section.getSectionNode() != nodes[i]) return false;
            TerrainSectionCache binding = bindings[i];
            if (allocationsChanged && binding != null && binding.revision != bindingRevisions[i]) return false;
            TerrainFaces terrain = faces[i];
            if (moved && terrain != null && visibilityKey(current, terrain, indexCounts[i], section.getRenderOrigin()) != visibilityKeys[i]) return false;
        }
        pass = current;
        return true;
    }

    int copyTo(List<ChunkSectionInfo> outputInfos, Map<ChunkSectionLayer, List<ChunkDrawGroup>> outputGroups) {
        if (outputInfos instanceof ArrayList<ChunkSectionInfo> array) {
            array.ensureCapacity(array.size() + infos.size());
            for (int i = 0; i < infos.size(); i++) array.add(infos.get(i));
        } else {
            outputInfos.addAll(infos);
        }
        for (ChunkSectionLayer layer : layers) outputGroups.get(layer).addAll(groups.get(layer));
        return largest;
    }

    void invalidate() {
        pass = null;
        infos.clear();
        for (ChunkSectionLayer layer : layers) groups.get(layer).clear();
        Arrays.fill(sections, 0, count, null);
        Arrays.fill(meshes, 0, count, null);
        Arrays.fill(faces, 0, count, null);
        Arrays.fill(bindings, 0, count, null);
        count = 0;
    }
}
