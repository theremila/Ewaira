package dev.evasex.ewaira.verification;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.evasex.ewaira.mesh.QuadEncoding;
import dev.evasex.ewaira.model.ModelGeometry;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Random;
import java.util.function.Consumer;
import org.joml.Quaternionf;
import org.lwjgl.system.MemoryUtil;

/** Reflection is confined to this development fixture, to cover both vertex-format APIs. */
public final class NativeVertexVerification {
    private NativeVertexVerification() {}

    public static void compare(Consumer<VertexConsumer> writer, int fixture) throws ReflectiveOperationException {
        compare(writer, fixture, "QUADS", null);
    }

    public static void compare(Consumer<VertexConsumer> writer, int fixture, String topology, String format)
            throws ReflectiveOperationException {
        Snapshot expected = encode(false, writer, fixture, topology, format);
        Snapshot actual = encode(true, writer, fixture, topology, format);
        if (!Arrays.equals(expected.bytes, actual.bytes) || !expected.state.equals(actual.state)) {
            int difference = Arrays.mismatch(expected.bytes, actual.bytes);
            throw new AssertionError("Native vertex/draw-state mismatch at fixture " + fixture
                    + "; state_equal=" + expected.state.equals(actual.state) + "; byte=" + difference
                    + "; expected_state=" + expected.state + "; actual_state=" + actual.state);
        }
    }

    private static Snapshot encode(boolean optimized, Consumer<VertexConsumer> writer, int fixture, String topologyName, String formatName)
            throws ReflectiveOperationException {
        QuadEncoding.setEnabled(optimized);
        ModelGeometry.setEnabled(optimized);
        Constructor<?> constructor = Arrays.stream(BufferBuilder.class.getConstructors())
                .filter(value -> value.getParameterCount() == 3).findFirst().orElseThrow();
        Object topology = Arrays.stream(constructor.getParameterTypes()[1].getEnumConstants())
                .filter(value -> ((Enum<?>) value).name().equals(topologyName)).findFirst().orElseThrow();
        String entityFormat = Arrays.stream(DefaultVertexFormat.class.getFields())
                .anyMatch(field -> field.getName().equals("NEW_ENTITY")) ? "NEW_ENTITY" : "ENTITY";
        Object format = DefaultVertexFormat.class.getField(formatName != null ? formatName
                : fixture % 2 == 0 ? "BLOCK" : entityFormat).get(null);
        try (ByteBufferBuilder arena = new ZeroedArena(64)) {
            BufferBuilder target = (BufferBuilder) constructor.newInstance(arena, topology, format);
            writer.accept(target);
            try (MeshData mesh = target.buildOrThrow()) {
                byte[] bytes = new byte[mesh.vertexBuffer().remaining()];
                mesh.vertexBuffer().get(bytes);
                return new Snapshot(bytes, mesh.drawState());
            }
        }
    }

    public static String verifyTransforms() throws ReflectiveOperationException {
        try {
            for (int i = 0; i < 512; i++) {
                PoseStack.Pose pose = pose(i);
                final int seed = i;
                compare(target -> {
                    Random random = new Random(seed);
                    for (int v = 0; v < 4; v++) {
                        float x = random.nextFloat(-2, 2), y = random.nextFloat(-2, 2), z = random.nextFloat(-2, 2);
                        target.addVertex(pose.pose(), x, y, z).setColor(random.nextInt())
                                .setUv(random.nextFloat(), random.nextFloat()).setOverlay(random.nextInt())
                                .setLight(random.nextInt()).setNormal(pose, x, y, z);
                    }
                }, i);
            }
            return "PASS: 512 transformed position/normal native buffers and draw states match vanilla.\n";
        } finally {
            QuadEncoding.setEnabled(true);
            ModelGeometry.setEnabled(true);
        }
    }

    public static PoseStack.Pose pose(int seed) {
        Random random = new Random(seed);
        PoseStack stack = new PoseStack();
        stack.translate(100007.25, -2.5, -100003.125);
        Quaternionf rotation = new Quaternionf().rotationXYZ(random.nextFloat(), random.nextFloat(), random.nextFloat());
        stack.last().pose().rotate(rotation);
        stack.last().normal().rotate(rotation);
        stack.scale(0.3f, seed % 2 == 0 ? -1.7f : 0.3f, 2.25f);
        if (seed % 3 == 0) stack.last().pose().m03(0.17f).m13(-0.3f).m23(0.7f);
        return stack.last();
    }

    public static void compareFailure(Consumer<VertexConsumer> writer) throws ReflectiveOperationException {
        RuntimeException expected = failure(false, writer), actual = failure(true, writer);
        if (expected.getClass() != actual.getClass() || !expected.getMessage().equals(actual.getMessage())) {
            throw new AssertionError("Model bulk path changed native builder validation", actual);
        }
    }

    private static RuntimeException failure(boolean optimized, Consumer<VertexConsumer> writer)
            throws ReflectiveOperationException {
        try {
            encode(optimized, writer, 1, "QUADS", null);
        } catch (RuntimeException exception) {
            return exception;
        }
        throw new AssertionError("Expected native builder validation to fail");
    }

    private static final class ZeroedArena extends ByteBufferBuilder {
        private ZeroedArena(int size) { super(size); }

        @Override public long reserve(int size) {
            long address = super.reserve(size);
            // Include padding in full-byte comparisons, even when a small arena grows mid-cube.
            MemoryUtil.memSet(address, 0, size);
            return address;
        }
    }

    private record Snapshot(byte[] bytes, MeshData.DrawState state) {}
}
