package dev.evasex.ewaira.model;

import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3fc;

final class ModelVertices {
    private ModelVertices() {}

    static ModelPart.Vertex[] vertices(ModelPart.Polygon face) { return face.vertices(); }
    static Vector3fc normal(ModelPart.Polygon face) { return face.normal(); }
    static float x(ModelPart.Vertex vertex) { return vertex.worldX(); }
    static float y(ModelPart.Vertex vertex) { return vertex.worldY(); }
    static float z(ModelPart.Vertex vertex) { return vertex.worldZ(); }
    static float u(ModelPart.Vertex vertex) { return vertex.u(); }
    static float v(ModelPart.Vertex vertex) { return vertex.v(); }

    static boolean samePosition(ModelPart.Vertex first, ModelPart.Vertex second) {
        return Float.floatToRawIntBits(x(first)) == Float.floatToRawIntBits(x(second))
                && Float.floatToRawIntBits(y(first)) == Float.floatToRawIntBits(y(second))
                && Float.floatToRawIntBits(z(first)) == Float.floatToRawIntBits(z(second));
    }
}
