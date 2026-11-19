package dev.evasex.ewaira.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Builder-owned scratch state; vanilla retains attribute conversion and native writes. */
public final class ModernQuadEncoder {
    private final Vector3f position = new Vector3f();
    private final Vector3f normal = new Vector3f();

    public VertexConsumer writePosition(VertexConsumer target, Matrix4fc matrix, float x, float y, float z) {
        matrix.transformPosition(x, y, z, position);
        return target.addVertex(position.x, position.y, position.z);
    }

    public VertexConsumer writeNormal(VertexConsumer target, PoseStack.Pose pose, float x, float y, float z) {
        pose.transformNormal(x, y, z, normal);
        return target.setNormal(normal.x, normal.y, normal.z);
    }

    public void write(VertexConsumer target, PoseStack.Pose pose, BakedQuad quad, QuadInstance instance) {
        pose.transformNormal(quad.direction().getUnitVec3f(), normal);
        int emission = quad.materialInfo().lightEmission();
        for (int i = 0; i < 4; i++) {
            Vector3fc source = quad.position(i);
            pose.pose().transformPosition(source, position);
            long uv = quad.packedUV(i);
            target.addVertex(position.x, position.y, position.z, instance.getColor(i),
                    UVPair.unpackU(uv), UVPair.unpackV(uv), instance.overlayCoords(),
                    instance.getLightCoordsWithEmission(i, emission), normal.x, normal.y, normal.z);
        }
    }
}
