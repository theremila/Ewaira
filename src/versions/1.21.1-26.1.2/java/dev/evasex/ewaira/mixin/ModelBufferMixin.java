package dev.evasex.ewaira.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.evasex.ewaira.model.ModelGeometry;
import dev.evasex.ewaira.model.ModelVertexSink;
import dev.evasex.ewaira.model.PackedModelWriter;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BufferBuilder.class)
abstract class ModelBufferMixin implements ModelVertexSink {
    @Shadow @Final private ByteBufferBuilder buffer;
    @Shadow @Final private VertexFormat.Mode mode;
    @Shadow @Final private boolean fastFormat;
    @Shadow @Final private boolean fullFormat;
    @Shadow @Final private int vertexSize;
    @Shadow private int vertices;
    @Shadow private long vertexPointer;
    @Shadow protected abstract void ensureBuilding();
    @Shadow protected abstract void endLastVertex();

    @Override
    public boolean ewaira$writeModel(Vector3f[] positions, byte[] indices, long[] uv, Vector3f[] normals,
            int color, int overlay, int light) {
        int count = indices.length;
        if (!ModelGeometry.isEnabled() || !PackedModelWriter.LITTLE_ENDIAN || !fastFormat
                || mode != VertexFormat.Mode.QUADS || vertices > 16777215 - count) return false;
        if (count == 0) return true;
        ensureBuilding();
        endLastVertex();
        long address = buffer.reserve(count * vertexSize);
        vertices += count;
        vertexPointer = address + (long) (count - 1) * vertexSize;
        PackedModelWriter.write(address, vertexSize, fullFormat, positions, indices, uv, normals, color, overlay, light);
        return true;
    }
}
