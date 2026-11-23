package remila.sex.cirrus.verification;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import remila.sex.cirrus.mesh.QuadEncoding;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Random;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import org.joml.Vector3f;

public final class RenderEncodingVerification {
    private RenderEncodingVerification() {}

    public static String verify() throws ReflectiveOperationException {
        if (Arrays.stream(BufferBuilder.class.getDeclaredMethods()).noneMatch(method -> method.getName().equals("putBakedQuad"))) {
            throw new AssertionError("Modern BufferBuilder mixin missing");
        }
        Random random = new Random(12813);
        try {
            for (int i = 0; i < 1008; i++) {
                BakedQuad quad = new BakedQuad(vector(random), vector(random), vector(random), vector(random),
                        uv(random), uv(random), uv(random), uv(random), Direction.values()[i % 6], material(i % 16));
                QuadInstance instance = new QuadInstance();
                for (int v = 0; v < 4; v++) {
                    instance.setColor(v, random.nextInt());
                    instance.setLightCoords(v, random.nextInt());
                }
                instance.setOverlayCoords(random.nextInt());
                PoseStack.Pose pose = NativeVertexVerification.pose(i);
                NativeVertexVerification.compare(target -> target.putBakedQuad(pose, quad, instance), i);
            }
            return "PASS: 1008 modern baked-quad native buffers/draw states match vanilla; six directions,\n"
                    + "16 emission levels, per-vertex colors/light, overlay, mirrored/nonuniform and non-affine transforms.\n";
        } finally {
            QuadEncoding.setEnabled(true);
        }
    }

    private static BakedQuad.MaterialInfo material(int emission) throws ReflectiveOperationException {
        RecordComponent[] fields = BakedQuad.MaterialInfo.class.getRecordComponents();
        Class<?>[] types = Arrays.stream(fields).map(RecordComponent::getType).toArray(Class<?>[]::new);
        Constructor<BakedQuad.MaterialInfo> constructor = BakedQuad.MaterialInfo.class.getConstructor(types);
        Object[] values = new Object[fields.length];
        for (int i = 0; i < fields.length; i++) {
            if (types[i] == int.class) values[i] = fields[i].getName().equals("lightEmission") ? emission : 0;
            else if (types[i] == boolean.class) values[i] = false;
        }
        return constructor.newInstance(values);
    }

    private static Vector3f vector(Random random) {
        return new Vector3f(random.nextFloat(-2, 2), random.nextFloat(-2, 2), random.nextFloat(-2, 2));
    }
    private static long uv(Random random) {
        return ((long) Float.floatToRawIntBits(random.nextFloat()) << 32)
                | Integer.toUnsignedLong(Float.floatToRawIntBits(random.nextFloat()));
    }
}
