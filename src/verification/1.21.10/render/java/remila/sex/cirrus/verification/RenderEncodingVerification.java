package remila.sex.cirrus.verification;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import remila.sex.cirrus.mesh.FastQuadEncoder;
import remila.sex.cirrus.mesh.QuadEncoding;
import java.util.Arrays;
import java.util.Random;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import org.joml.Quaternionf;

/** Shared by plain JUnit and the GPU-free Fabric verification entrypoint. */
public final class RenderEncodingVerification {
    private RenderEncodingVerification() {}

    public static String verify() {
        boolean throughMixin = true;
        if (throughMixin && Arrays.stream(BufferBuilder.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().equals("putBulkData"))) {
            throw new AssertionError("BufferBuilder mixin did not add its bulk method");
        }
        Random random = new Random(1234);
        VertexFormat[] formats = {DefaultVertexFormat.BLOCK, DefaultVertexFormat.NEW_ENTITY,
                DefaultVertexFormat.POSITION_COLOR};
        try {
            for (int i = 0; i < 1008; i++) {
                PoseStack poses = new PoseStack();
                int transform = (i / 12) % 7;
                if (transform > 0) poses.translate(17.25, -2.5, 1000.125);
                if (transform > 1) poses.mulPose(new Quaternionf().rotationXYZ(0.27f, -0.9f, 1.31f));
                if (transform > 2) poses.scale(0.3f, transform == 3 ? 0.3f : -1.7f, 2.25f);
                if (transform > 4) poses.last().pose().m03(0.17f).m13(-0.3f).m23(0.7f);
                if (transform == 6) poses.last().normal().rotateX(0.37f);
                int[] vertices = new int[32];
                for (int v = 0; v < 4; v++) {
                    for (int c = 0; c < 3; c++) vertices[v * 8 + c] = Float.floatToRawIntBits(random.nextFloat(-2, 2));
                    vertices[v * 8 + 3] = random.nextInt();
                    vertices[v * 8 + 4] = Float.floatToRawIntBits(random.nextFloat(-1, 2));
                    vertices[v * 8 + 5] = Float.floatToRawIntBits(random.nextFloat(-1, 2));
                }
                BakedQuad quad = new BakedQuad(vertices, -1, Direction.values()[i % 6], null, true, i % 16);
                float[] brightness = {random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat()};
                int[] lights = {random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt()};
                float red = random.nextFloat(-0.2f, 1.2f), green = random.nextFloat(), blue = random.nextFloat();
                float alpha = random.nextFloat(-0.2f, 1.2f);
                int overlay = random.nextInt();
                VertexFormat format = formats[(i / 84) % formats.length];
                VertexFormat.Mode mode = i % 13 == 0 ? VertexFormat.Mode.LINES : VertexFormat.Mode.QUADS;
                Snapshot expected = encode(false, throughMixin, poses.last(), quad, brightness,
                        red, green, blue, alpha, lights, overlay, i % 2 == 0, format, mode, i % 7 == 0);
                Snapshot actual = encode(true, throughMixin, poses.last(), quad, brightness,
                        red, green, blue, alpha, lights, overlay, i % 2 == 0, format, mode, i % 7 == 0);
                if (!expected.state.equals(actual.state) || !Arrays.equals(expected.bytes, actual.bytes)) {
                    throw new AssertionError("Vertex mismatch at fixture " + i + ", format=" + format + ", transform=" + transform);
                }
            }
            return "PASS: 1008 baked-quad fixtures, including 144 uniform-light/no-array calls; all emitted attribute bytes and draw metadata match vanilla.\n"
                    + "Six face normals, tinted/untinted colors, AO, packed lighting/overlay, alpha, translations,\n"
                    + "rotations, mirrored/nonuniform scales, non-affine matrices, BLOCK/NEW_ENTITY/POSITION_COLOR,\n"
                    + "QUADS/LINES, native buffer growth; only uninitialized padding bytes excluded.\n"
                    + "through_fabric_mixin=" + throughMixin + "\n";
        } finally {
            QuadEncoding.setEnabled(true);
        }
    }

    private static Snapshot encode(boolean optimized, boolean throughMixin, PoseStack.Pose pose,
            BakedQuad quad, float[] brightness, float red, float green, float blue, float alpha,
            int[] lights, int overlay, boolean colored, VertexFormat format, VertexFormat.Mode mode, boolean uniform) {
        QuadEncoding.setEnabled(optimized);
        try (ByteBufferBuilder arena = new ByteBufferBuilder(32)) {
            BufferBuilder target = new BufferBuilder(arena, mode, format);
            if (uniform) {
                if (optimized && !throughMixin) {
                    new FastQuadEncoder().writeUniform(target, pose, quad, red, green, blue, alpha, lights[0], overlay);
                } else {
                    target.putBulkData(pose, quad, red, green, blue, alpha, lights[0], overlay);
                }
            } else if (optimized && !throughMixin) {
                new FastQuadEncoder().write(target, pose, quad, brightness, red, green, blue, alpha, lights, overlay, colored);
            } else {
                target.putBulkData(pose, quad, brightness, red, green, blue, alpha, lights, overlay, colored);
            }
            try (MeshData mesh = target.buildOrThrow()) {
                byte[] bytes = new byte[mesh.vertexBuffer().remaining()];
                mesh.vertexBuffer().get(bytes);
                if (format != DefaultVertexFormat.POSITION_COLOR) {
                    int stride = format.getVertexSize();
                    for (int i = stride - 1; i < bytes.length; i += stride) bytes[i] = 0;
                }
                return new Snapshot(bytes, mesh.drawState());
            }
        }
    }

    private record Snapshot(byte[] bytes, MeshData.DrawState state) {}
}
