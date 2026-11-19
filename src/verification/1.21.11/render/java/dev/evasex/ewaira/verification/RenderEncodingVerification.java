package dev.evasex.ewaira.verification;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.evasex.ewaira.mesh.QuadEncoding;
import java.util.Arrays;
import java.util.Random;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import org.joml.Vector3f;

public final class RenderEncodingVerification {
    private RenderEncodingVerification() {}

    public static String verify() throws ReflectiveOperationException {
        if (Arrays.stream(BufferBuilder.class.getDeclaredMethods()).noneMatch(method -> method.getName().equals("putBulkData"))) {
            throw new AssertionError("Record-quad BufferBuilder mixin missing");
        }
        Random random = new Random(12813);
        try {
            for (int i = 0; i < 1008; i++) {
                BakedQuad quad = new BakedQuad(vector(random), vector(random), vector(random), vector(random),
                        uv(random), uv(random), uv(random), uv(random), -1, Direction.values()[i % 6], null, true, i % 16);
                float[] brightness = {random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat()};
                int[] lights = {random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt()};
                float r = random.nextFloat(-0.2f, 1.2f), g = random.nextFloat(), b = random.nextFloat();
                float alpha = random.nextFloat(-0.2f, 1.2f);
                int overlay = random.nextInt();
                PoseStack.Pose pose = NativeVertexVerification.pose(i);
                boolean uniform = i % 7 == 0;
                NativeVertexVerification.compare(target -> {
                    if (uniform) target.putBulkData(pose, quad, r, g, b, alpha, lights[0], overlay);
                    else target.putBulkData(pose, quad, brightness, r, g, b, alpha, lights, overlay);
                }, i);
            }
            return "PASS: 1008 record baked-quad native buffers/draw states match vanilla, including 144 uniform-light calls;\n"
                    + "six directions, 16 emission levels, AO, colors, light, overlay and transformed geometry.\n";
        } finally {
            QuadEncoding.setEnabled(true);
        }
    }

    private static Vector3f vector(Random random) {
        return new Vector3f(random.nextFloat(-2, 2), random.nextFloat(-2, 2), random.nextFloat(-2, 2));
    }
    private static long uv(Random random) {
        return ((long) Float.floatToRawIntBits(random.nextFloat()) << 32)
                | Integer.toUnsignedLong(Float.floatToRawIntBits(random.nextFloat()));
    }
}
