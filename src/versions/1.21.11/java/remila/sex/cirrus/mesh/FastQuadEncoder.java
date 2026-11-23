package remila.sex.cirrus.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.ARGB;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/** Adapter for the 1.21.11 record quad format, before QuadInstance was introduced. */
public final class FastQuadEncoder {
    private final Vector3f position = new Vector3f();
    private final Vector3f normal = new Vector3f();

    public boolean accepts(BakedQuad quad, float[] brightness, int[] lights) {
        return brightness.length >= 4 && lights.length >= 4;
    }

    public VertexConsumer writePosition(VertexConsumer target, Matrix4fc matrix, float x, float y, float z) {
        matrix.transformPosition(x, y, z, position);
        return target.addVertex(position.x, position.y, position.z);
    }

    public VertexConsumer writeNormal(VertexConsumer target, PoseStack.Pose pose, float x, float y, float z) {
        pose.transformNormal(x, y, z, normal);
        return target.setNormal(normal.x, normal.y, normal.z);
    }

    public void writeUniform(VertexConsumer target, PoseStack.Pose pose, BakedQuad quad,
            float red, float green, float blue, float alpha, int light, int overlay) {
        pose.transformNormal(quad.direction().getUnitVec3f(), normal);
        int color = ARGB.colorFromFloat(alpha, red, green, blue);
        int packedLight = LightTexture.lightCoordsWithEmission(light, quad.lightEmission());
        for (int i = 0; i < 4; i++) writeVertex(target, pose, quad, i, color, packedLight, overlay);
    }

    public void write(VertexConsumer target, PoseStack.Pose pose, BakedQuad quad, float[] brightness,
            float red, float green, float blue, float alpha, int[] lights, int overlay) {
        pose.transformNormal(quad.direction().getUnitVec3f(), normal);
        for (int i = 0; i < 4; i++) {
            float shade = brightness[i];
            int color = ARGB.colorFromFloat(alpha, shade * red, shade * green, shade * blue);
            writeVertex(target, pose, quad, i, color,
                    LightTexture.lightCoordsWithEmission(lights[i], quad.lightEmission()), overlay);
        }
    }

    private void writeVertex(VertexConsumer target, PoseStack.Pose pose, BakedQuad quad,
            int index, int color, int light, int overlay) {
        pose.pose().transformPosition(quad.position(index), position);
        long uv = quad.packedUV(index);
        target.addVertex(position.x, position.y, position.z, color, UVPair.unpackU(uv), UVPair.unpackV(uv),
                overlay, light, normal.x, normal.y, normal.z);
    }
}
