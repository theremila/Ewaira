package remila.sex.cirrus.model;

import org.joml.Vector3f;

/** Optional bulk path implemented only by the native BufferBuilder. */
public interface ModelVertexSink {
    boolean cirrus$writeModel(Vector3f[] positions, byte[] indices, long[] uv, Vector3f[] normals,
            int color, int overlay, int light);
}
