package remila.sex.cirrus.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.nio.ByteOrder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Vec3i;
import net.minecraft.util.FastColor;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** One encoder per BufferBuilder: scratch state follows the builder's thread confinement. */
public final class FastQuadEncoder {
    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    private final Vector3f normal = new Vector3f();
    private final Vector3f position = new Vector3f();

    static { QuadEncoding.markAvailable(); }

    public boolean accepts(BakedQuad quad) {
        return LITTLE_ENDIAN && quad.getVertices().length == 32;
    }

    public boolean accepts(BakedQuad quad, float[] brightness, int[] lights) {
        return accepts(quad) && brightness.length >= 4 && lights.length >= 4;
    }

    public VertexConsumer writePosition(VertexConsumer target, Matrix4f transform, float x, float y, float z) {
        transform.transformPosition(x, y, z, position);
        return target.addVertex(position.x, position.y, position.z);
    }

    public VertexConsumer writeNormal(VertexConsumer target, PoseStack.Pose pose, float x, float y, float z) {
        pose.transformNormal(x, y, z, normal);
        return target.setNormal(normal.x, normal.y, normal.z);
    }

    public void writeUniform(VertexConsumer target, PoseStack.Pose pose, BakedQuad quad,
            float red, float green, float blue, float alpha, int light, int overlay) {
        Vec3i direction = quad.getDirection().getNormal();
        pose.transformNormal(direction.getX(), direction.getY(), direction.getZ(), normal);
        int color = FastColor.ARGB32.color((int) (alpha * 255), (int) (red * 255),
                (int) (green * 255), (int) (blue * 255));
        int[] vertices = quad.getVertices();
        for (int i = 0; i < 4; i++) {
            writeVertex(target, pose.pose(), vertices, i * 8, color, overlay, light);
        }
    }

    private void writeVertex(VertexConsumer target, Matrix4f transform, int[] vertices,
            int offset, int color, int overlay, int light) {
        transform.transformPosition(Float.intBitsToFloat(vertices[offset]),
                Float.intBitsToFloat(vertices[offset + 1]), Float.intBitsToFloat(vertices[offset + 2]), position);
        target.addVertex(position.x, position.y, position.z, color,
                Float.intBitsToFloat(vertices[offset + 4]), Float.intBitsToFloat(vertices[offset + 5]),
                overlay, light, normal.x, normal.y, normal.z);
    }

    public void write(VertexConsumer target, PoseStack.Pose pose, BakedQuad quad,
            float[] brightness, float red, float green, float blue, float alpha,
            int[] lights, int overlay, boolean useQuadColor) {
        int[] vertices = quad.getVertices();
        Vec3i direction = quad.getDirection().getNormal();
        pose.transformNormal(direction.getX(), direction.getY(), direction.getZ(), normal);
        Matrix4f transform = pose.pose();
        int alphaByte = (int) (alpha * 255);
        for (int i = 0; i < 4; i++) {
            int offset = i * 8;
            float r, g, b;
            if (useQuadColor) {
                int color = vertices[offset + 3];
                // Preserve vanilla's multiplication order and float-to-int conversion exactly.
                r = (color & 255) * brightness[i] * red;
                g = ((color >>> 8) & 255) * brightness[i] * green;
                b = ((color >>> 16) & 255) * brightness[i] * blue;
            } else {
                r = brightness[i] * red * 255;
                g = brightness[i] * green * 255;
                b = brightness[i] * blue * 255;
            }
            int color = FastColor.ARGB32.color(alphaByte, (int) r, (int) g, (int) b);
            writeVertex(target, transform, vertices, offset, color, overlay, lights[i]);
        }
    }
}
