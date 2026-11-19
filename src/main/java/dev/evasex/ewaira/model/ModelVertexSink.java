package dev.evasex.ewaira.model;

import org.joml.Vector3f;

/** Optional bulk path implemented only by the native BufferBuilder. */
public interface ModelVertexSink {
    boolean ewaira$writeModel(Vector3f[] positions, byte[] indices, long[] uv, Vector3f[] normals,
            int color, int overlay, int light);
}
