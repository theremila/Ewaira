package dev.evasex.ewaira.verification;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.evasex.ewaira.model.ModelGeometry;
import dev.evasex.ewaira.model.ModelVertexSink;
import com.mojang.blaze3d.vertex.BufferBuilder;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Random;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;

public final class ModelGeometryVerification {
    private ModelGeometryVerification() {}

    public static String verify() throws ReflectiveOperationException {
        if (!ModelVertexSink.class.isAssignableFrom(BufferBuilder.class)
                || Arrays.stream(ModelPart.Cube.class.getDeclaredMethods())
                    .noneMatch(method -> method.getName().contains("ewaira$compileCorners"))) {
            throw new AssertionError("Model buffer/cube mixins were not applied");
        }
        try {
            for (int i = 0; i < 512; i++) {
                Random random = new Random(i);
                EnumSet<Direction> faces = EnumSet.allOf(Direction.class);
                if (i % 3 == 0) faces = EnumSet.of(Direction.values()[i % 6]);
                ModelPart.Cube cube = new ModelPart.Cube(i % 16, i % 32,
                        random.nextFloat(-32, 32), random.nextFloat(-32, 32), random.nextFloat(-32, 32),
                        i % 7 == 0 ? 0 : random.nextFloat(0, 32), random.nextFloat(0, 32), random.nextFloat(0, 32),
                        random.nextFloat(-1, 1), random.nextFloat(-1, 1), random.nextFloat(-1, 1),
                        i % 2 == 0, 64, 32, faces);
                PoseStack.Pose pose = NativeVertexVerification.pose(i);
                int color = random.nextInt(), light = random.nextInt(), overlay = random.nextInt();
                NativeVertexVerification.compare(target -> cube.compile(pose, target, light, overlay, color), i);
                // Reuse the same cube with a different pose: transformed positions must not survive a compile.
                PoseStack.Pose changed = NativeVertexVerification.pose(i + 1301);
                NativeVertexVerification.compare(target -> cube.compile(changed, target, light, overlay, color), i);
            }
            verifyBuilderBoundaries();
            return "PASS: 1024 model-cube native buffers/draw states match vanilla; mirrored, deformed, partial and changed poses.\n"
                    + "PASS: native arena growth, mixed writes, line/custom formats, consumer fallback, geometry edits and builder errors.\n";
        } finally {
            ModelGeometry.setEnabled(true);
        }
    }
    private static void verifyBuilderBoundaries() throws ReflectiveOperationException {
        ModelPart.Cube cube = new ModelPart.Cube(0, 0, -4, -8, -2, 8, 16, 4,
                0.1f, 0.2f, 0.3f, true, 64, 32, EnumSet.allOf(Direction.class));
        PoseStack.Pose pose = NativeVertexVerification.pose(317);
        for (String mode : new String[]{"QUADS", "LINES"}) {
            for (String format : new String[]{null, "BLOCK", "POSITION_COLOR"}) {
                NativeVertexVerification.compare(target -> {
                    for (int i = 0; i < 4; i++) target.addVertex(i, 2, 3, 0xFFEEDDCC,
                            0.25f, 0.75f, 17, 33, 0.5f, 0.25f, -1);
                    for (int i = 0; i < 16; i++) cube.compile(pose, target, i * 613, i * 111, 0xEEDDCCBB);
                    for (int i = 0; i < 4; i++) target.addVertex(i, 2, 3, 0xAABBCCDD,
                            0.25f, 0.75f, 41, 27, -0.75f, 1, 0);
                }, 1, mode, format);
            }
        }
        NativeVertexVerification.compare(target -> {
            VertexConsumer forwarding = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                    new Class<?>[]{VertexConsumer.class}, (proxy, method, args) -> method.invoke(target, args));
            cube.compile(pose, forwarding, 0xF000F0, 7, 0x99887766);
        }, 1);
        NativeVertexVerification.compareFailure(target -> {
            target.addVertex(1, 2, 3);
            cube.compile(pose, target, 0, 0, -1);
        });
        NativeVertexVerification.compareFailure(target -> {
            try (var mesh = ((BufferBuilder) target).build()) {
                if (mesh != null) throw new AssertionError("Empty builder emitted geometry");
            }
            cube.compile(pose, target, 0, 0, -1);
        });
        Field polygons = ModelPart.Cube.class.getDeclaredField("polygons");
        polygons.setAccessible(true);
        Object firstFace = ((Object[]) polygons.get(cube))[0];
        Object[] vertices;
        try {
            vertices = (Object[]) firstFace.getClass().getMethod("vertices").invoke(firstFace);
        } catch (NoSuchMethodException legacy) {
            vertices = (Object[]) firstFace.getClass().getField("vertices").get(firstFace);
        }
        Object original = vertices[0];
        vertices[0] = original.getClass().getConstructor(float.class, float.class, float.class, float.class, float.class)
                .newInstance(37.25f, -29.5f, 9f, 0.123f, 0.456f);
        NativeVertexVerification.compare(target -> cube.compile(pose, target, 17, 33, -1), 1);
        vertices[0] = original;
        NativeVertexVerification.compare(target -> cube.compile(pose, target, 17, 33, -1), 1);
        try {
            var position = (org.joml.Vector3f) original.getClass().getField("pos").get(original);
            position.add(3, -2, 5);
            NativeVertexVerification.compare(target -> cube.compile(pose, target, 17, 33, -1), 1);
        } catch (NoSuchFieldException immutableRecord) {
            // Newer vertices store immutable position components instead of mutable vectors.
        }
    }

}
