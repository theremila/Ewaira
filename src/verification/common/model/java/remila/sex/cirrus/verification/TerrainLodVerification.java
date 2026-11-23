package remila.sex.cirrus.verification;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import remila.sex.cirrus.lod.TerrainLod;
import remila.sex.cirrus.terrain.TerrainFaces;
import remila.sex.cirrus.terrain.TerrainMeshAccess;
import remila.sex.cirrus.terrain.TerrainOptimization;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native mesh lifecycle and actual transformed draw preparation, without creating a GPU. */
public final class TerrainLodVerification {
    private TerrainLodVerification() {}

    public static String verify() throws ReflectiveOperationException {
        TerrainLod.setEnabled(true); TerrainLod.setDistance(64); TerrainOptimization.setEnabled(true);
        Class<?> renderer = Class.forName("net.minecraft.client.renderer.LevelRenderer", false, TerrainLodVerification.class.getClassLoader());
        Method trim = Arrays.stream(renderer.getDeclaredMethods()).filter(m -> m.getName().contains("cirrus$trimSolid")).findFirst().orElseThrow();
        trim.setAccessible(true);
        Constructor<?> builderConstructor = Arrays.stream(BufferBuilder.class.getConstructors()).filter(c -> c.getParameterCount() == 3).findFirst().orElseThrow();
        Object quads = Arrays.stream(builderConstructor.getParameterTypes()[1].getEnumConstants()).filter(e -> ((Enum<?>) e).name().equals("QUADS")).findFirst().orElseThrow();
        try (ByteBufferBuilder arena = new ByteBufferBuilder(64)) {
            BufferBuilder builder = (BufferBuilder) builderConstructor.newInstance(arena, quads, DefaultVertexFormat.BLOCK);
            Class<?> directionType = Class.forName("net.minecraft.core.Direction"), faceType = Class.forName("net.minecraft.client.renderer.FaceInfo");
            for (Object direction : directionType.getEnumConstants()) {
                int axis = ((Enum<?>) directionType.getMethod("getAxis").invoke(direction)).ordinal();
                // Axis enum order is X,Y,Z in each supported native target.
                int sign = (int) directionType.getMethod("getStepX").invoke(direction)
                        + (int) directionType.getMethod("getStepY").invoke(direction)
                        + (int) directionType.getMethod("getStepZ").invoke(direction);
                Object face = faceType.getMethod("fromFacing", directionType).invoke(null, direction);
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) for (int v = 0; v < 4; v++) {
                    Object vertex = faceType.getMethod("getVertexInfo", int.class).invoke(face, v);
                    float[] pos = TerrainVerification.nativeCorner(vertex);
                    pos[axis] = sign > 0 ? 16 : 0;
                    pos[(axis + 1) % 3] += x; pos[(axis + 2) % 3] += y;
                    builder.addVertex(pos[0], pos[1], pos[2]).setColor(0xff447755)
                            .setUv(v == 1 || v == 2 ? .125f : 0, v >= 2 ? .125f : 0).setLight(0xf000f0)
                            .setNormal(axis == 0 ? sign : 0, axis == 1 ? sign : 0, axis == 2 ? sign : 0);
                }
            }
            ByteBufferBuilder lodArena;
            try (MeshData mesh = builder.buildOrThrow()) {
                Object compiled, solid;
                Class<?> resultsType = Class.forName("net.minecraft.client.renderer.chunk.SectionCompiler$Results");
                Object results = resultsType.getConstructor().newInstance();
                @SuppressWarnings("unchecked") Map<Object, MeshData> layers = (Map<Object, MeshData>) resultsType.getField("renderedLayers").get(results);
                if (trim.getReturnType() != void.class) {
                    solid = Class.forName("net.minecraft.client.renderer.chunk.ChunkSectionLayer").getField("SOLID").get(null);
                    layers.put(solid, mesh);
                    Constructor<?> ctor = Class.forName("net.minecraft.client.renderer.chunk.CompiledSectionMesh").getConstructors()[0];
                    compiled = ctor.getParameterCount() == 3 ? ctor.newInstance(null, results, 0L) : ctor.newInstance(null, results);
                } else {
                    solid = Class.forName("net.minecraft.client.renderer.RenderType").getMethod("solid").invoke(null);
                    layers.put(solid, mesh);
                    Class<?> compiler = Class.forName("net.minecraft.client.renderer.chunk.SectionCompiler");
                    Method group = Arrays.stream(compiler.getDeclaredMethods()).filter(m -> m.getName().contains("cirrus$groupSolid")).findFirst().orElseThrow();
                    group.setAccessible(true);
                    group.invoke(TerrainVerification.allocate(compiler), new CallbackInfoReturnable<>("compile", false, results));
                    compiled = mesh;
                }
                TerrainFaces faces = ((TerrainMeshAccess) compiled).cirrus$terrainFaces();
                if (faces == null || !faces.hasLod() || mesh.indexBuffer() == null) throw new AssertionError("Native compiler did not attach LOD");
                Field arenaField = MeshData.class.getDeclaredField("cirrus$indexArena");
                arenaField.setAccessible(true); lodArena = (ByteBufferBuilder) arenaField.get(mesh);
                if (lodArena == null) throw new AssertionError("Missing native index owner");
                if (mesh.drawState().vertexCount() != 6144 || mesh.drawState().indexCount() != 12096) throw new AssertionError("Unexpected native LOD allocation");
                if (!mesh.drawState().indexType().name().equals("SHORT")) throw new AssertionError("Wrong native LOD index type");
                var indices = mesh.indexBuffer().duplicate().order(ByteOrder.nativeOrder()).asShortBuffer();
                if (indices.remaining() != 12096) throw new AssertionError("LOD index arena has wrong size");
                while (indices.hasRemaining()) { int index = Short.toUnsignedInt(indices.get()); if (index >= 6144) throw new AssertionError("Out-of-range native LOD index"); }
                if (trim.getReturnType() != void.class) verifyDraw(trim, renderer, compiled, solid, faces, mesh.drawState().indexCount());
                else if (TerrainFaces.indexCount(faces.range(12096, 300, 300, 300)) != 288) throw new AssertionError("Legacy far LOD range incorrect");
            }
            Field pointer = ByteBufferBuilder.class.getDeclaredField("pointer");
            pointer.setAccessible(true);
            if (pointer.getLong(lodArena) != 0) throw new AssertionError("Mesh close leaked the native LOD index arena");
            // The native vertex arena remains usable after the separate LOD arena is freed.
            for (int repeat = 0; repeat < 4; repeat++) {
                BufferBuilder next = (BufferBuilder) builderConstructor.newInstance(arena, quads, DefaultVertexFormat.BLOCK);
                for (int v = 0; v < 4; v++) next.addVertex(0, 0, 0).setColor(-1).setUv(0, 0).setLight(0).setNormal(0, 1, 0);
                try (MeshData ignored = next.buildOrThrow()) { }
            }
        } finally { TerrainLod.setEnabled(true); TerrainLod.setDistance(64); TerrainOptimization.setEnabled(true); }
        return "PASS: native chunk LOD indices preserve 6144 original vertices; 3072 triangles become 768 / 192 before directional culling; unsigned 16-bit index arena uses 24192 bytes and is freed on mesh close.\n"
                + (trim.getReturnType() != void.class ? "PASS: transformed draw commands select near/middle/far LOD and original offsets, custom indices, disabled LOD, disabled culling, nonfinite camera and wireframe fallback.\n" : "PASS: legacy compiler emits native LOD index arena; actual OpenGL draw remains unverified.\n");
    }

    private static void verifyDraw(Method trim, Class<?> renderer, Object compiled, Object solid, TerrainFaces faces, int count) throws ReflectiveOperationException {
        Object instance = TerrainVerification.allocate(renderer);
        boolean indirect = trim.getParameterCount() == 4;
        boolean indexedDraw = false;
        Object camera = null, state = null;
        if (indirect) {
            Class<?> stateType = Class.forName("net.minecraft.client.renderer.state.level.LevelRenderState");
            state = stateType.getConstructor().newInstance();
            camera = Class.forName("net.minecraft.client.renderer.state.level.CameraRenderState").getConstructor().newInstance();
            stateType.getField("cameraRenderState").set(state, camera);
            Field field = renderer.getDeclaredField("levelRenderState"); field.setAccessible(true); field.set(instance, state);
            if (Arrays.stream(renderer.getDeclaredMethods()).anyMatch(m -> m.getName().equals("extractSectionDrawGroups"))) { indexedDraw = true; }
        }
        for (double distance : new double[]{24, 96, 300, -300, Double.NaN}) {
            for (int mode = 0; mode < 3; mode++) {
                TerrainLod.setEnabled(mode != 1); TerrainOptimization.setEnabled(mode != 2);
                Object draw = NativeTerrainDraw.create(trim.getReturnType(), count, 12);
                Object[] call;
                if (indirect) {
                    Object eye = Class.forName("net.minecraft.world.phys.Vec3").getConstructor(double.class, double.class, double.class).newInstance(distance, distance, distance);
                    camera.getClass().getField("pos").set(camera, eye);
                    call = new Object[]{draw, compiled, solid, BlockPos.ZERO};
                } else call = new Object[]{draw, compiled, solid, BlockPos.ZERO, distance, distance, distance};
                Object result = trim.invoke(instance, call);
                NativeTerrainDraw.verifyPreserved(draw, result);
                long expected = faces.range(count, distance, distance, distance);
                if (NativeTerrainDraw.integer(result, "indexCount") != TerrainFaces.indexCount(expected)
                        || NativeTerrainDraw.integer(result, "firstIndex") != 12 + TerrainFaces.firstIndex(expected)) throw new AssertionError("LOD native draw command mismatch");
            }
        }
        TerrainLod.setEnabled(true); TerrainOptimization.setEnabled(true);
        if (indirect && indexedDraw) {
            state.getClass().getField("renderWireframeTerrain").setBoolean(state, true);
            Object draw = NativeTerrainDraw.create(trim.getReturnType(), count, 12);
            Object result = trim.invoke(instance, draw, compiled, solid, BlockPos.ZERO);
            NativeTerrainDraw.verifyPreserved(draw, result);
            if (NativeTerrainDraw.integer(result, "indexCount") != 9216 || NativeTerrainDraw.integer(result, "firstIndex") != 12) throw new AssertionError("Wireframe drew overlapping LODs");
        }
    }

}
