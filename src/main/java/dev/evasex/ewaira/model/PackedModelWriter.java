package dev.evasex.ewaira.model;

import java.nio.ByteOrder;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

public final class PackedModelWriter {
    public static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    private PackedModelWriter() {}

    public static void write(long address, int stride, boolean entityFormat, Vector3f[] positions,
            byte[] indices, long[] uv, Vector3f[] normals, int color, int overlay, int light) {
        int rgba = (color & 0xFF00FF00) | ((color >>> 16) & 255) | ((color & 255) << 16);
        long packedLight = Integer.toUnsignedLong(overlay) | ((long) light << 32);
        int normalOffset = entityFormat ? 32 : stride == 32 ? 28 : -1;
        for (int face = 0; face < indices.length / 4; face++) {
            Vector3f normal = normals[face];
            int packedNormal = component(normal.x) | (component(normal.y) << 8) | (component(normal.z) << 16);
            for (int v = 0; v < 4; v++) {
                int index = face * 4 + v;
                Vector3f position = positions[indices[index]];
                MemoryUtil.memPutFloat(address, position.x);
                MemoryUtil.memPutFloat(address + 4, position.y);
                MemoryUtil.memPutFloat(address + 8, position.z);
                MemoryUtil.memPutInt(address + 12, rgba);
                MemoryUtil.memPutLong(address + 16, uv[index]);
                if (entityFormat) MemoryUtil.memPutLong(address + 24, packedLight);
                else MemoryUtil.memPutInt(address + 24, light);
                if (normalOffset >= 0) {
                    // Padding belongs to the allocator; vanilla writes exactly three normal bytes.
                    MemoryUtil.memPutShort(address + normalOffset, (short) packedNormal);
                    MemoryUtil.memPutByte(address + normalOffset + 2, (byte) (packedNormal >>> 16));
                }
                address += stride;
            }
        }
    }

    private static int component(float value) {
        return (int) (Mth.clamp(value, -1, 1) * 127) & 255;
    }
}
