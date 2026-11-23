package remila.sex.cirrus.model;

import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3fc;

final class ModelVertices {
    private ModelVertices() {}

    static ModelPart.Vertex[] vertices(ModelPart.Polygon face) { return face.vertices; }
    static Vector3fc normal(ModelPart.Polygon face) { return face.normal; }
    static float x(ModelPart.Vertex vertex) { return vertex.pos.x / 16.0f; }
    static float y(ModelPart.Vertex vertex) { return vertex.pos.y / 16.0f; }
    static float z(ModelPart.Vertex vertex) { return vertex.pos.z / 16.0f; }
    static float u(ModelPart.Vertex vertex) { return vertex.u; }
    static float v(ModelPart.Vertex vertex) { return vertex.v; }

    static boolean samePosition(ModelPart.Vertex first, ModelPart.Vertex second) {
        // Legacy UV remaps share mutable corner vectors; keep later position edits visible.
        return first.pos == second.pos;
    }
}
