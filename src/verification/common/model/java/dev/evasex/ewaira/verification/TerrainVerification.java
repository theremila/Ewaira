package dev.evasex.ewaira.verification;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import dev.evasex.ewaira.terrain.TerrainFaces;
import dev.evasex.ewaira.lod.TerrainLod;
import dev.evasex.ewaira.terrain.TerrainMeshAccess;
import dev.evasex.ewaira.terrain.TerrainMeshes;
import dev.evasex.ewaira.terrain.TerrainOptimization;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sun.misc.Unsafe;

/** Development-only native mesh and transformed draw-preparation checks; never creates a GPU device. */
public final class TerrainVerification {
    private TerrainVerification() {}
    private static volatile Object drawSink;

    public static String verify() throws ReflectiveOperationException, InterruptedException {
        Class<?> renderer = Class.forName("net.minecraft.client.renderer.LevelRenderer", false, TerrainVerification.class.getClassLoader());
        Method trim = Arrays.stream(renderer.getDeclaredMethods()).filter(m -> m.getName().contains("ewaira$trimSolid")).findFirst().orElseThrow();
        trim.setAccessible(true);
        Constructor<?> builderConstructor = Arrays.stream(BufferBuilder.class.getConstructors())
                .filter(c -> c.getParameterCount() == 3).findFirst().orElseThrow();
        Object quads = Arrays.stream(builderConstructor.getParameterTypes()[1].getEnumConstants())
                .filter(e -> ((Enum<?>) e).name().equals("QUADS")).findFirst().orElseThrow();
        String allocationReport = "";
        try (ByteBufferBuilder arena = new ByteBufferBuilder(64)) {
            BufferBuilder builder = (BufferBuilder) builderConstructor.newInstance(arena, quads, DefaultVertexFormat.BLOCK);
            Class<?> directionType = Class.forName("net.minecraft.core.Direction");
            Class<?> faceType = Class.forName("net.minecraft.client.renderer.FaceInfo");
            for (Object direction : directionType.getEnumConstants()) {
                Object face = faceType.getMethod("fromFacing", directionType).invoke(null, direction);
                for (int v = 0; v < 4; v++) {
                    Object vertex = faceType.getMethod("getVertexInfo", int.class).invoke(face, v);
                    float[] pos = nativeCorner(vertex);
                    builder.addVertex(pos[0], pos[1], pos[2]).setColor(0xff123456)
                            .setUv(v / 4f, 0).setLight(0xf000f0).setNormal(0, 1, 0);
                }
            }
            try (MeshData mesh = builder.buildOrThrow()) {
                byte[] before = new byte[mesh.vertexBuffer().remaining()]; mesh.vertexBuffer().get(0, before);
                TerrainOptimization.setEnabled(false); TerrainLod.setEnabled(false);
                if (TerrainMeshes.sort(mesh) != null) throw new AssertionError("Disabled terrain sorter ran");
                TerrainOptimization.setEnabled(true); TerrainLod.setEnabled(true);
                Object compiled;
                Object solid;
                if (trim.getReturnType() != void.class) {
                    Class<?> layer = Class.forName("net.minecraft.client.renderer.chunk.ChunkSectionLayer");
                    solid = layer.getField("SOLID").get(null);
                    Class<?> resultsType = Class.forName("net.minecraft.client.renderer.chunk.SectionCompiler$Results");
                    Object results = resultsType.getConstructor().newInstance();
                    @SuppressWarnings("unchecked") Map<Object,MeshData> layers = (Map<Object,MeshData>) resultsType.getField("renderedLayers").get(results);
                    layers.put(solid, mesh);
                    Class<?> compiledType = Class.forName("net.minecraft.client.renderer.chunk.CompiledSectionMesh");
                    Constructor<?> ctor = compiledType.getConstructors()[0];
                    compiled = ctor.getParameterCount() == 3 ? ctor.newInstance(null, results, 0L) : ctor.newInstance(null, results);
                    TerrainFaces faces = ((TerrainMeshAccess) compiled).ewaira$terrainFaces();
                    if (faces == null) throw new AssertionError("Compiled mesh did not receive face groups");
                    allocationReport = verifyDraw(trim, renderer, compiled, solid);
                } else {
                    Class<?> compilerType = Class.forName("net.minecraft.client.renderer.chunk.SectionCompiler");
                    Class<?> resultsType = Class.forName("net.minecraft.client.renderer.chunk.SectionCompiler$Results");
                    Object results = resultsType.getConstructor().newInstance();
                    solid = Class.forName("net.minecraft.client.renderer.RenderType").getMethod("solid").invoke(null);
                    @SuppressWarnings("unchecked") Map<Object,MeshData> layers = (Map<Object,MeshData>) resultsType.getField("renderedLayers").get(results);
                    layers.put(solid, mesh);
                    Method group = Arrays.stream(compilerType.getDeclaredMethods()).filter(m -> m.getName().contains("ewaira$groupSolid")).findFirst().orElseThrow();
                    group.setAccessible(true);
                    group.invoke(allocate(compilerType), new CallbackInfoReturnable<>("compile", false, results));
                    if (((TerrainMeshAccess) mesh).ewaira$terrainFaces() == null) throw new AssertionError("Compiler did not attach solid groups");
                    Class<?> vbo = Class.forName("com.mojang.blaze3d.vertex.VertexBuffer", false, renderer.getClassLoader());
                    if (Arrays.stream(vbo.getDeclaredMethods()).noneMatch(m -> m.getName().equals("ewaira$drawTerrain"))) throw new AssertionError("Terrain VBO hook missing");
                }
                byte[] after = new byte[before.length]; mesh.vertexBuffer().get(0, after);
                int stride = before.length / 24;
                byte[][] expected = records(before,stride*4), actual = records(after,stride*4);
                Comparator<byte[]> order = Arrays::compareUnsigned;
                Arrays.sort(expected,order); Arrays.sort(actual,order);
                if (!Arrays.deepEquals(expected,actual)) throw new AssertionError("Grouping changed native quad bytes");
                allocationReport += verifyAllocations(before, stride);
                if (mesh.drawState().vertexCount() != 24 || mesh.drawState().indexCount() != 36) throw new AssertionError("Grouping changed native allocation sizes");
            }
        } finally { TerrainOptimization.setEnabled(true); }
        return TerrainLodVerification.verify() + BiomeSamplingVerification.verify() + ItemLodVerification.verify() + allocationReport + SectionVisibilityVerification.verify() + "PASS: native solid-terrain quad bytes and allocation sizes preserved; vanilla FaceInfo winding verified; compiler hooks transformed.\n"
                + (trim.getReturnType() != void.class
                ? "PASS: transformed terrain draw preparation trims 36 to 18 cube indices; native offsets and disabled commands preserved.\n"
                : "PASS: legacy terrain VBO/draw hooks transformed; OpenGL draw execution not exercised.\n");
    }

    private static String verifyAllocations(byte[] cube, int stride) {
        int copies = 512;
        byte[] original = new byte[cube.length * copies];
        for (int i = 0; i < copies; i++) System.arraycopy(cube, 0, original, i * cube.length, cube.length);
        ByteBuffer vertices = ByteBuffer.allocateDirect(original.length).order(ByteOrder.nativeOrder());
        for (int i = 0; i < 30; i++) {
            vertices.put(0, original);
            if (TerrainFaces.sort(vertices, stride, 24 * copies) == null) throw new AssertionError("Allocation fixture did not group terrain");
        }
        ThreadMXBean allocations = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        allocations.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        long before = allocations.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 200; i++) {
            vertices.put(0, original);
            if (TerrainFaces.sort(vertices, stride, 24 * copies) == null) throw new AssertionError("Terrain sorter stopped");
        }
        long bytes = (allocations.getThreadAllocatedBytes(thread) - before) / 200;
        if (bytes >= 4096) throw new AssertionError("Repeated terrain rebuild allocated large scratch arrays: " + bytes);
        return "PASS: repeated 3,072-quad terrain rebuilds reuse bounded worker scratch.\n"
                + "terrain_sort_allocated_bytes_per_rebuild=" + bytes + ", native_mesh_bytes=" + original.length + "\n";
    }

    private static String verifyDraw(Method trim, Class<?> renderer, Object compiled, Object solid) throws ReflectiveOperationException {
        Object instance = allocate(renderer);
        boolean indirect = trim.getParameterCount() == 4;
        Object camera = null;
        if (indirect) {
            Class<?> stateType = Class.forName("net.minecraft.client.renderer.state.level.LevelRenderState");
            Object state = stateType.getConstructor().newInstance();
            Class<?> cameraType = Class.forName("net.minecraft.client.renderer.state.level.CameraRenderState");
            camera = cameraType.getConstructor().newInstance();
            Object eye = Class.forName("net.minecraft.world.phys.Vec3").getConstructor(double.class,double.class,double.class).newInstance(10d,10d,10d);
            cameraType.getField("pos").set(camera,eye); stateType.getField("cameraRenderState").set(state,camera);
            Field field = renderer.getDeclaredField("levelRenderState"); field.setAccessible(true); field.set(instance,state);
        }
        Object draw = NativeTerrainDraw.create(trim.getReturnType(), 36, 0);
        Object[] call = indirect ? new Object[]{draw, compiled, solid, BlockPos.ZERO}
                : new Object[]{draw, compiled, solid, BlockPos.ZERO, 10d, 10d, 10d};
        Object result = trim.invoke(instance, call);
        if (NativeTerrainDraw.integer(result, "indexCount") != 18 || NativeTerrainDraw.integer(result, "firstIndex") != 0)
            throw new AssertionError("Native draw did not remove hidden cube sides");
        NativeTerrainDraw.verifyPreserved(draw, result);
        draw = NativeTerrainDraw.create(trim.getReturnType(), 36, 12); call[0] = draw;
        if (indirect) {
            Object eye = Class.forName("net.minecraft.world.phys.Vec3")
                    .getConstructor(double.class, double.class, double.class).newInstance(-10d, -10d, -10d);
            camera.getClass().getField("pos").set(camera, eye);
        } else { call[4] = -10d; call[5] = -10d; call[6] = -10d; }
        result = trim.invoke(instance, call);
        if (NativeTerrainDraw.integer(result, "indexCount") != 18 || NativeTerrainDraw.integer(result, "firstIndex") != 30)
            throw new AssertionError("Terrain trim lost the native index offset");
        NativeTerrainDraw.verifyPreserved(draw, result);
        TerrainOptimization.setEnabled(false);
        if (trim.invoke(instance, call) != draw) throw new AssertionError("Unchanged command was allocated again");
        TerrainOptimization.setEnabled(true);
        ThreadMXBean allocations = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        allocations.setThreadAllocatedMemoryEnabled(true);
        for (int i = 0; i < 20_000; i++) drawSink = trim.invoke(instance, call);
        long thread = Thread.currentThread().threadId();
        long before = allocations.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 200_000; i++) drawSink = trim.invoke(instance, call);
        long bytes = (allocations.getThreadAllocatedBytes(thread) - before) / 200_000;
        drawSink = null;
        if (bytes >= 128) throw new AssertionError("Excess command adaptation allocation: " + bytes);
        return "terrain_changed_draw_allocated_bytes=" + bytes + "; reflection fixture, original command preallocated\n";
    }

    static float[] nativeCorner(Object vertex) throws ReflectiveOperationException {
        if (vertex.getClass().isRecord()) {
            Vector3f pos = (Vector3f) vertex.getClass()
                    .getMethod("select", Vector3fc.class, Vector3fc.class)
                    .invoke(vertex, new Vector3f(0), new Vector3f(1));
            return new float[]{pos.x, pos.y, pos.z};
        }
        Class<?> constants = Class.forName("net.minecraft.client.renderer.FaceInfo$Constants");
        String[] axes = {"X", "Y", "Z"};
        float[] pos = new float[3];
        for (int axis = 0; axis < 3; axis++) {
            int index = vertex.getClass().getField(axes[axis].toLowerCase() + "Face").getInt(vertex);
            pos[axis] = index == constants.getField("MAX_" + axes[axis]).getInt(null) ? 1 : 0;
        }
        return pos;
    }

    static Object allocate(Class<?> type) throws ReflectiveOperationException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return ((Unsafe) field.get(null)).allocateInstance(type);
    }
    private static byte[][] records(byte[] bytes, int size) {
        byte[][] result = new byte[bytes.length/size][];
        for(int i=0;i<result.length;i++) result[i]=Arrays.copyOfRange(bytes,i*size,(i+1)*size);
        return result;
    }
}
