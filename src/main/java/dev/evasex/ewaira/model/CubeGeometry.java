package dev.evasex.ewaira.model;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

/** Immutable topology; transformed positions belong to the calling thread and live for one compile. */
public final class CubeGeometry {
    private static final ThreadLocal<Workspace> WORKSPACE = ThreadLocal.withInitial(Workspace::new);
    private final ModelPart.Polygon[] faces;
    private final ModelPart.Vertex[] vertices;
    private final ModelPart.Vertex[] corners = new ModelPart.Vertex[8];
    private final byte[] cornerIndices;
    private final long[] uv;
    private final int cornerCount;

    public CubeGeometry(ModelPart.Polygon[] polygons) {
        faces = polygons.clone();
        int size = 0;
        for (ModelPart.Polygon face : faces) size += ModelVertices.vertices(face).length;
        vertices = new ModelPart.Vertex[size];
        cornerIndices = new byte[size];
        uv = new long[size];
        int cursor = 0;
        boolean supported = faces.length <= 6;
        for (ModelPart.Polygon face : faces) {
            supported &= ModelVertices.vertices(face).length == 4;
            for (ModelPart.Vertex vertex : ModelVertices.vertices(face)) {
                vertices[cursor] = vertex;
                uv[cursor] = Integer.toUnsignedLong(Float.floatToRawIntBits(ModelVertices.u(vertex)))
                        | ((long) Float.floatToRawIntBits(ModelVertices.v(vertex)) << 32);
                cursor++;
            }
        }
        int count = 0;
        for (int i = 0; i < vertices.length && supported; i++) {
            ModelPart.Vertex vertex = vertices[i];
            int index = 0;
            while (index < count && !ModelVertices.samePosition(corners[index], vertex)) index++;
            if (index == count) {
                if (count == corners.length) {
                    supported = false;
                    break;
                }
                corners[count++] = vertex;
            }
            cornerIndices[i] = (byte) index;
        }
        cornerCount = supported ? count : -1;
    }

    public boolean matches(ModelPart.Polygon[] polygons) {
        if (polygons.length != faces.length) return false;
        int cursor = 0;
        for (int i = 0; i < faces.length; i++) {
            if (polygons[i] != faces[i]) return false;
            ModelPart.Vertex[] current = ModelVertices.vertices(polygons[i]);
            for (ModelPart.Vertex vertex : current) {
                if (cursor == vertices.length || vertex != vertices[cursor++]) return false;
            }
        }
        return cursor == vertices.length;
    }

    public boolean render(PoseStack.Pose pose, VertexConsumer target, int light, int overlay, int color) {
        // Arbitrary consumers may change the pose while receiving vertices.
        if (cornerCount < 0 || target.getClass() != BufferBuilder.class) return false;
        Workspace workspace = WORKSPACE.get();
        if (workspace.busy) return false;
        workspace.busy = true;
        try {
            for (int i = 0; i < cornerCount; i++) {
                ModelPart.Vertex corner = corners[i];
                pose.pose().transformPosition(ModelVertices.x(corner), ModelVertices.y(corner), ModelVertices.z(corner), workspace.positions[i]);
            }
            for (int i = 0; i < faces.length; i++) {
                var normal = ModelVertices.normal(faces[i]);
                pose.transformNormal(normal.x(), normal.y(), normal.z(), workspace.normals[i]);
            }
            return ((ModelVertexSink) target).ewaira$writeModel(workspace.positions, cornerIndices, uv,
                    workspace.normals, color, overlay, light);
        } finally {
            workspace.busy = false;
        }
    }

    private static final class Workspace {
        private final Vector3f[] positions = new Vector3f[8];
        private final Vector3f[] normals = new Vector3f[6];
        private boolean busy;

        private Workspace() {
            for (int i = 0; i < positions.length; i++) positions[i] = new Vector3f();
            for (int i = 0; i < normals.length; i++) normals[i] = new Vector3f();
        }
    }
}
