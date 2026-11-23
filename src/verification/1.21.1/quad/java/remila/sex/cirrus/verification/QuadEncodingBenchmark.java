package remila.sex.cirrus.verification;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import remila.sex.cirrus.mesh.QuadEncoding;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.Random;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import org.joml.Vector3f;

/** Stage-only CPU timing: no window, graphics device, world tick or FPS measurement. */
public final class QuadEncodingBenchmark {
    private static final int QUADS = 8192;
    private QuadEncodingBenchmark() {}

    public static String run() {
        ThreadMXBean timer = ManagementFactory.getThreadMXBean();
        if (!timer.isCurrentThreadCpuTimeSupported()) return "CPU timing unavailable.\n";
        if (!timer.isThreadCpuTimeEnabled()) timer.setThreadCpuTimeEnabled(true);
        Random random = new Random(9284);
        BakedQuad[] quads = new BakedQuad[64];
        for (int i = 0; i < quads.length; i++) {
            int[] data = new int[32];
            for (int v = 0; v < 4; v++) {
                for (int axis = 0; axis < 3; axis++) data[v * 8 + axis] = Float.floatToRawIntBits(random.nextFloat());
                data[v * 8 + 3] = random.nextInt();
                data[v * 8 + 4] = Float.floatToRawIntBits(random.nextFloat());
                data[v * 8 + 5] = Float.floatToRawIntBits(random.nextFloat());
            }
            quads[i] = new BakedQuad(data, -1, Direction.values()[i % 6], null, true);
        }
        PoseStack poses = new PoseStack();
        poses.translate(17.25, -2.5, 1000.125);
        poses.scale(0.3f, -1.7f, 2.25f);
        float[] brightness = {0.4f, 0.7f, 0.8f, 1};
        int[] lights = {0xF000F0, 0xA000D0, 0x700090, 0xC000C0};
        long[][] samples = new long[2][11];
        try (ByteBufferBuilder arena = new ByteBufferBuilder(QUADS * 4 * DefaultVertexFormat.BLOCK.getVertexSize())) {
            for (int pair = -16; pair < samples[0].length; pair++) {
                for (int turn = 0; turn < 2; turn++) {
                    int mode = (pair & 1) ^ turn;
                    QuadEncoding.setEnabled(mode == 1);
                    BufferBuilder target = new BufferBuilder(arena, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
                    long start = timer.getCurrentThreadCpuTime();
                    for (int i = 0; i < QUADS; i++) {
                        target.putBulkData(poses.last(), quads[i & 63], brightness, 0.7f, 0.8f, 0.9f, 1,
                                lights, 0, (i & 1) == 0);
                    }
                    long elapsed = timer.getCurrentThreadCpuTime() - start;
                    try (MeshData mesh = target.buildOrThrow()) {
                        if (mesh.drawState().vertexCount() != QUADS * 4
                                || mesh.vertexBuffer().getInt(0) != Float.floatToRawIntBits(poses.last().pose()
                                        .transformPosition(Float.intBitsToFloat(quads[0].getVertices()[0]),
                                                Float.intBitsToFloat(quads[0].getVertices()[1]),
                                                Float.intBitsToFloat(quads[0].getVertices()[2]), new Vector3f()).x)) {
                            throw new AssertionError("Timing fixture emitted an unexpected mesh");
                        }
                    }
                    arena.clear();
                    if (pair >= 0) samples[mode][pair] = elapsed;
                }
            }
        } finally {
            QuadEncoding.setEnabled(true);
        }
        double[] medians = new double[2];
        StringBuilder result = new StringBuilder("CPU-only quad encoding; 16 warmup pairs, 11 alternating sample pairs, 8192 quads/sample.\n");
        for (int mode = 0; mode < 2; mode++) {
            result.append(mode == 0 ? "vanilla_cpu_ns=" : "optimized_cpu_ns=").append(Arrays.toString(samples[mode])).append('\n');
            Arrays.sort(samples[mode]);
            medians[mode] = samples[mode][samples[mode].length / 2] / (double) QUADS;
        }
        return result.append("median_cpu_ns_per_quad: vanilla=").append(medians[0])
                .append(", optimized=").append(medians[1]).append('\n')
                .append("Single JVM, synthetic geometry; CPU clocks/JIT and desktop activity uncontrolled. Not gameplay FPS.\n").toString();
    }
}
