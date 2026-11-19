package dev.evasex.ewaira.verification;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.evasex.ewaira.model.ModelGeometry;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.EnumSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;

/** CPU-only native vertex writing, with no graphics device or gameplay FPS claim. */
public final class ModelGeometryBenchmark {
    private static final int CUBES = 4096;
    private ModelGeometryBenchmark() {}

    public static String run() throws ReflectiveOperationException {
        ThreadMXBean timer = ManagementFactory.getThreadMXBean();
        if (!timer.isCurrentThreadCpuTimeSupported()) return "Model CPU timer unavailable.\n";
        if (!timer.isThreadCpuTimeEnabled()) timer.setThreadCpuTimeEnabled(true);
        Constructor<?> constructor = Arrays.stream(BufferBuilder.class.getConstructors())
                .filter(value -> value.getParameterCount() == 3).findFirst().orElseThrow();
        Object topology = Arrays.stream(constructor.getParameterTypes()[1].getEnumConstants())
                .filter(value -> ((Enum<?>) value).name().contains("QUAD")).findFirst().orElseThrow();
        String entityFormat = Arrays.stream(DefaultVertexFormat.class.getFields())
                .anyMatch(field -> field.getName().equals("NEW_ENTITY")) ? "NEW_ENTITY" : "ENTITY";
        Object format = DefaultVertexFormat.class.getField(entityFormat).get(null);
        ModelPart.Cube[] cubes = new ModelPart.Cube[64];
        PoseStack.Pose[] poses = new PoseStack.Pose[64];
        for (int i = 0; i < cubes.length; i++) {
            cubes[i] = new ModelPart.Cube(i % 16, i % 32, -4, -8, -2, 8, 16, 4,
                    0.1f, 0.2f, 0.3f, (i & 1) == 0, 64, 32, EnumSet.allOf(Direction.class));
            poses[i] = NativeVertexVerification.pose(i);
        }
        long[][] samples = new long[2][11];
        try (ByteBufferBuilder arena = new ByteBufferBuilder(CUBES * 24 * 64)) {
            for (int pair = -20; pair < samples[0].length; pair++) for (int turn = 0; turn < 2; turn++) {
                int mode = (pair & 1) ^ turn;
                ModelGeometry.setEnabled(mode == 1);
                BufferBuilder target = (BufferBuilder) constructor.newInstance(arena, topology, format);
                long start = timer.getCurrentThreadCpuTime();
                for (int i = 0; i < CUBES; i++) cubes[i & 63].compile(poses[(i + pair + 64) & 63], target,
                        0xF000F0, 0, 0xFFEEDDCC);
                long elapsed = timer.getCurrentThreadCpuTime() - start;
                try (MeshData mesh = target.buildOrThrow()) {
                    if (mesh.drawState().vertexCount() != CUBES * 24) throw new AssertionError("Missing model vertices");
                }
                arena.clear();
                if (pair >= 0) samples[mode][pair] = elapsed;
            }
        } finally {
            ModelGeometry.setEnabled(true);
        }
        StringBuilder result = new StringBuilder("CPU-only model cubes; 20 warmup pairs, 11 alternating sample pairs, 4096 cubes/sample.\n");
        double[] medians = new double[2];
        for (int mode = 0; mode < 2; mode++) {
            result.append(mode == 0 ? "vanilla_model_cpu_ns=" : "optimized_model_cpu_ns=")
                    .append(Arrays.toString(samples[mode])).append('\n');
            Arrays.sort(samples[mode]);
            medians[mode] = samples[mode][samples[mode].length / 2] / (double) CUBES;
        }
        return result.append("median_cpu_ns_per_cube: vanilla=").append(medians[0])
                .append(", optimized=").append(medians[1]).append('\n')
                .append("Synthetic model stage, one JVM, uncontrolled JIT/CPU clocks. Not gameplay FPS.\n").toString();
    }
}
