package remila.sex.cirrus.verification;

import com.mojang.blaze3d.vertex.UberGpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.IndexType;
import remila.sex.cirrus.terrain.ChunkDrawPreparation;
import remila.sex.cirrus.terrain.TerrainBatching;
import remila.sex.cirrus.terrain.TerrainSliceAccess;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.lang.management.ManagementFactory;
import com.sun.management.ThreadMXBean;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.renderer.DynamicGpuData.ChunkSectionInfo;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelRenderer.ChunkDrawGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSectionBufferSlice;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import sun.misc.Unsafe;

/** Native transformed renderer without a graphics device; fixtures never enter release artifacts. */
public final class ChunkPreparationVerification {
    private static final Unsafe UNSAFE;
    static {
        try { Field f = Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); UNSAFE = (Unsafe) f.get(null); }
        catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }
    private ChunkPreparationVerification() {}

    public static String verify() throws Exception {
        FakeDispatcher dispatcher = (FakeDispatcher) UNSAFE.allocateInstance(FakeDispatcher.class);
        dispatcher.slices = new IdentityHashMap<>();
        set(SectionRenderDispatcher.class, dispatcher, "cirrus$bufferOwner", new Object());
        ObjectArrayList<RenderSection> visible = new ObjectArrayList<>();
        GpuBuffer[][] vertex = new GpuBuffer[3][2];
        for (int l = 0; l < 3; l++) for (int b = 0; b < 2; b++) vertex[l][b] = buffer();
        GpuBuffer indices = buffer();
        for (int s = 0; s < 48; s++) {
            CompiledSectionMesh mesh = new CompiledSectionMesh(null, new SectionCompiler.Results(), 0);
            @SuppressWarnings("unchecked") Map<ChunkSectionLayer, SectionMesh.SectionDraw> draws = (Map<ChunkSectionLayer, SectionMesh.SectionDraw>) get(CompiledSectionMesh.class, mesh, "draws");
            Map<ChunkSectionLayer, RenderSectionBufferSlice> slices = new EnumMap<>(ChunkSectionLayer.class);
            for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
                boolean custom = layer.translucent() || s % 3 == 0;
                draws.put(layer, new SectionMesh.SectionDraw(6 * (s + 1), s % 2 == 0 ? IndexType.SHORT : IndexType.INT, custom));
                slices.put(layer, new RenderSectionBufferSlice(vertex[layer.ordinal()][s % 2], (long) s * layer.vertexFormat().getVertexSize(), custom ? indices : null, s * 8L));
            }
            dispatcher.slices.put(mesh, slices);
            RenderSection section = (RenderSection) UNSAFE.allocateInstance(RenderSection.class);
            set(RenderSection.class, section, "sectionMesh", new AtomicReference<SectionMesh>(mesh));
            set(RenderSection.class, section, "renderOrigin", new BlockPos.MutableBlockPos(s * 16, 64, s * -16));
            visible.add(section);
        }
        LevelRenderer renderer = (LevelRenderer) UNSAFE.allocateInstance(LevelRenderer.class);
        set(LevelRenderer.class, renderer, "visibleSections", visible);
        set(LevelRenderer.class, renderer, "sectionRenderDispatcher", dispatcher);
        LevelRenderState state = new LevelRenderState();
        state.cameraRenderState = new CameraRenderState();
        state.cameraRenderState.pos = new Vec3(-32, 96, 48);
        set(LevelRenderer.class, renderer, "levelRenderState", state);
        set(LevelRenderer.class, renderer, "optionsRenderState", new OptionsRenderState());
        set(LevelRenderer.class, renderer, "cirrus$preparation", new ChunkDrawPreparation());
        Method extract = LevelRenderer.class.getDeclaredMethod("extractSectionDrawGroups", boolean.class, List.class, Map.class);
        extract.setAccessible(true);
        try {
            for (int pass = 0; pass < 6; pass++) {
                for (boolean ordered : new boolean[]{false, true}) {
                    TerrainBatching.setEnabled(false);
                    Snapshot expected = extract(renderer, extract, ordered);
                    TerrainBatching.setEnabled(true);
                    Snapshot actual = extract(renderer, extract, ordered);
                    check(expected.equals(actual), "native draw mismatch pass=" + pass + " ordered=" + ordered);
                    int calls = dispatcher.calls;
                    check(expected.equals(extract(renderer, extract, ordered)), "snapshot warmup changed commands");
                    var preparation = (ChunkDrawPreparation) get(LevelRenderer.class, renderer, "cirrus$preparation");
                    long before = (long) get(ChunkDrawPreparation.class, preparation, "frame");
                    check(expected.equals(extract(renderer, extract, ordered)), "stable snapshot changed commands");
                    check(before == (long) get(ChunkDrawPreparation.class, preparation, "frame"), "stable snapshot was not replayed");
                    state.cameraRenderState.pos = state.cameraRenderState.pos.add(0.125, 0, 0);
                    ChunkDrawPreparation.invalidateAllocations();
                    check(expected.equals(extract(renderer, extract, ordered)), "moving replay changed command bytes");
                    check(before == (long) get(ChunkDrawPreparation.class, preparation, "frame"),
                            "unrelated uploads or camera motion prevented replay");
                    check(calls == dispatcher.calls, "warm cache performed allocation lookups");
                }
                Collections.rotate(visible, 7);
                // Native freeAllocation is also used for transparent re-sorts, not just section unloads.
                SectionMesh mesh = visible.getFirst().getSectionMesh();
                UberGpuBuffer<SectionMesh> allocator = new UberGpuBuffer<>("fixture", 32, 1024, 8, null);
                Method free = UberGpuBuffer.class.getDeclaredMethod("freeAllocation", Object.class);
                free.setAccessible(true); free.invoke(allocator, mesh);
                check(((TerrainSliceAccess) mesh).cirrus$sectionCache().get(get(SectionRenderDispatcher.class, dispatcher, "cirrus$bufferOwner"), 0) == null, "native allocation free did not invalidate");
                dispatcher.slices.get(mesh).replaceAll((layer, slice) -> new RenderSectionBufferSlice(slice.vertexBuffer(), slice.vertexBufferOffset() + layer.vertexFormat().getVertexSize(), slice.indexBuffer(), slice.indexBufferOffset() + 16));
                allocator.close();
            }
            SectionMesh mesh = visible.getFirst().getSectionMesh();
            UberGpuBuffer<SectionMesh> allocator = new UberGpuBuffer<>("fixture-close", 32, 1024, 8, null);
            @SuppressWarnings("unchecked") Map<SectionMesh, Object> allocations = (Map<SectionMesh, Object>) get(UberGpuBuffer.class, allocator, "allocationMap");
            allocations.put(mesh, null);
            allocator.close();
            check(((TerrainSliceAccess) mesh).cirrus$sectionCache().get(get(SectionRenderDispatcher.class, dispatcher, "cirrus$bufferOwner"), 1) == null, "native allocator close did not invalidate");
            check(dispatcher.locks == 0, "dispatcher lock leaked");
            Class.forName("net.minecraft.client.renderer.DynamicGpuData");
            Class.forName("net.minecraft.client.renderer.entity.EntityRenderer");
            return "PASS: native chunk command preparation matches across 48 sections, three layers, custom/sequential indices, ordered/unordered transparency, reordering and offset changes; stable groups replayed while moving and after unrelated uploads; native allocation free/close invalidate.\n" + benchmark(renderer, extract);
        } finally { TerrainBatching.setEnabled(true); }
    }

    private static String benchmark(LevelRenderer renderer, Method method) throws Exception {
        List<ChunkSectionInfo> infos = new ArrayList<>();
        Map<ChunkSectionLayer, List<ChunkDrawGroup>> output = new EnumMap<>(ChunkSectionLayer.class);
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) output.put(layer, new ArrayList<>());
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long[] nativeNs = new long[11], optimizedNs = new long[11];
        long nativeBytes = 0, optimizedBytes = 0;
        for (int i = -20; i < 11; i++) for (int step = 0; step < 2; step++) {
            boolean enabled = ((i + step) & 1) == 0;
            TerrainBatching.setEnabled(enabled);
            long bytes = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()), start = System.nanoTime();
            for (int j = 0; j < 128; j++) {
                infos.clear(); for (List<ChunkDrawGroup> groups : output.values()) groups.clear();
                if ((int) method.invoke(renderer, true, infos, output) == 0) throw new AssertionError("No commands");
            }
            long elapsed = System.nanoTime() - start;
            bytes = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - bytes;
            if (i >= 0) {
                (enabled ? optimizedNs : nativeNs)[i] = elapsed / 128;
                if (enabled) optimizedBytes += bytes; else nativeBytes += bytes;
            }
        }
        Arrays.sort(nativeNs); Arrays.sort(optimizedNs);
        return "CPU-only native command extraction, 48 synthetic sections; 20 alternating warmup pairs, 11 pairs of 128 calls; not gameplay FPS.\n"
                + "chunk_preparation_median_ns: native=" + nativeNs[5] + ", optimized=" + optimizedNs[5] + "\n"
                + "chunk_preparation_allocated_bytes_per_frame: native=" + nativeBytes / (11 * 128) + ", optimized=" + optimizedBytes / (11 * 128) + "\n";
    }

    private record Snapshot(int largest, List<ChunkSectionInfo> infos, List<Object> commands) {}
    private static Snapshot extract(LevelRenderer renderer, Method method, boolean ordered) throws Exception {
        List<ChunkSectionInfo> infos = new ArrayList<>();
        Map<ChunkSectionLayer, List<ChunkDrawGroup>> output = new EnumMap<>(ChunkSectionLayer.class);
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) output.put(layer, new ArrayList<>());
        int largest = (int) method.invoke(renderer, ordered, infos, output);
        List<Object> commands = new ArrayList<>();
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) for (ChunkDrawGroup group : output.get(layer)) {
            commands.add(layer); commands.add(group.vertexBuffer()); commands.add(group.indexBuffer()); commands.add(group.indexType());
            commands.add(List.copyOf(group.draws()));
        }
        return new Snapshot(largest, infos, commands);
    }
    private static GpuBuffer buffer() {
        return (GpuBuffer) Proxy.newProxyInstance(GpuBuffer.class.getClassLoader(), new Class<?>[]{GpuBuffer.class}, (proxy, method, args) -> switch (method.getName()) {
            case "size" -> 1_048_576L;
            case "usage", "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "isClosed" -> false;
            case "slice" -> new GpuBufferSlice((GpuBuffer) proxy, args == null ? 0 : (long) args[0], args == null ? 1_048_576 : (long) args[1]);
            case "toString" -> "FixtureBuffer@" + System.identityHashCode(proxy);
            default -> throw new UnsupportedOperationException(method.toString());
        });
    }
    private static Object get(Class<?> type, Object target, String name) throws Exception { Field f = type.getDeclaredField(name); f.setAccessible(true); return f.get(target); }
    private static void set(Class<?> type, Object target, String name, Object value) throws Exception { Field f = type.getDeclaredField(name); f.setAccessible(true); f.set(target, value); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static class FakeDispatcher extends SectionRenderDispatcher {
        Map<SectionMesh, Map<ChunkSectionLayer, RenderSectionBufferSlice>> slices;
        int calls, locks;
        private FakeDispatcher() { super(null, null, null, null); }
        @Override public RenderSectionBufferSlice getRenderSectionSlice(SectionMesh mesh, ChunkSectionLayer layer) { calls++; return slices.get(mesh).get(layer); }
        @Override public void lock() { locks++; }
        @Override public void unlock() { locks--; }
    }
}
