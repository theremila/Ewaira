package remila.sex.cirrus.terrain;

import remila.sex.cirrus.memory.OffHeapTerrainScratch;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.lwjgl.system.MemoryUtil;

/** Index-only planar LOD: keep native vertices and select the outer corners of complete patches, using off-heap scratch. */
public final class TerrainLodIndices {
    private static final int MAX_QUADS = (1 << Short.SIZE) / 4;
    private static final int PLANE_CELLS = 16 * 16;
    private static final int FACING_CELLS = 17 * PLANE_CELLS;

    private TerrainLodIndices() {}

    /**
     * Scratch indices are valid only until the next build on this thread; upload/copy immediately.
     * {@code nativeAddress} is non-zero for off-heap builds and allows single-call {@link #copyTo(long)}.
     */
    public record Result(short[] indices, long nativeAddress, int count, int middleStart, int farStart,
                         int[] middleEnds, int[] farEnds) {
        public void copyTo(long destinationAddress) {
            if (nativeAddress != 0L) {
                MemoryUtil.memCopy(nativeAddress, destinationAddress, (long) count * Short.BYTES);
            } else {
                MemoryUtil.memShortBuffer(destinationAddress, count).put(indices, 0, count);
            }
        }
    }

    /** Public entrypoint returning heap short[] for tests and off-heap pointer for direct buffers. */
    public static Result build(ByteBuffer source, int stride, int vertexCount) {
        return buildInternal(source, stride, vertexCount, true);
    }

    /** Fast native path for chunk compilation: keeps indices purely off-heap (zero heap short[] allocation). */
    public static Result buildOffHeap(ByteBuffer source, int stride, int vertexCount) {
        return buildInternal(source, stride, vertexCount, false);
    }

    private static Result buildInternal(ByteBuffer source, int stride, int vertexCount, boolean materializeHeapArray) {
        if ((stride != 28 && stride != 32) || vertexCount <= 0 || vertexCount % 4 != 0 || vertexCount / 4 > MAX_QUADS
                || (long) stride * vertexCount > source.remaining()) return null;
        if (source.isDirect() && ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            return buildNative(source, stride, vertexCount, materializeHeapArray);
        }
        return buildFromHeapBuffer(source, stride, vertexCount);
    }

    private static Result buildNative(ByteBuffer source, int stride, int vertexCount, boolean materializeHeapArray) {
        long base = MemoryUtil.memAddress(source);
        int quads = vertexCount / 4;
        OffHeapTerrainScratch scratch = OffHeapTerrainScratch.current();
        long gridAddr = scratch.lodGridCleared();
        long workAddr = scratch.lodWork(quads);
        long cellsAddr = workAddr;
        long usedAddr = cellsAddr + (long) quads * Integer.BYTES;
        long bucketsAddr = usedAddr + quads;
        long precomputedBuckets = scratch.matchingSortedBuckets(base, stride, quads);
        if (precomputedBuckets != 0L) {
            MemoryUtil.memCopy(precomputedBuckets, bucketsAddr, quads);
        }
        for (int q = 0; q < quads; q++) {
            int bucket;
            if (precomputedBuckets != 0L) {
                bucket = MemoryUtil.memGetByte(bucketsAddr + q) & 0xFF;
            } else {
                bucket = TerrainFaces.classifyNative(base + (long) q * 4 * stride, stride);
                if (bucket < 0) return null;
                MemoryUtil.memPutByte(bucketsAddr + q, (byte) bucket);
            }
            int address = cellNative(base, q * 4, stride, bucket);
            MemoryUtil.memPutInt(cellsAddr + (long) q * Integer.BYTES, address);
            if (address >= 0) {
                long cellPtr = gridAddr + (long) address * Integer.BYTES;
                int existing = MemoryUtil.memGetInt(cellPtr);
                MemoryUtil.memPutInt(cellPtr, existing == -1 ? q : -2);
            }
        }
        long indicesAddr = scratch.lodIndices(quads * 18);
        int cursor = 0;
        for (int q = 0; q < quads; q++) {
            int v0 = q * 4;
            cursor = emitNative(indicesAddr, cursor, v0, v0 + 1, v0 + 2, v0 + 3);
        }
        int middleStart = cursor;
        int[] middleEnds = new int[7];
        cursor = simplifyNative(base, gridAddr, cellsAddr, usedAddr, bucketsAddr, indicesAddr, quads, 2, cursor, middleEnds, stride);
        int farStart = cursor;
        int[] farEnds = new int[7];
        cursor = simplifyNative(base, gridAddr, cellsAddr, usedAddr, bucketsAddr, indicesAddr, quads, 4, cursor, farEnds, stride);
        if ((farStart - middleStart) * 8L > middleStart * 7L) return null;
        short[] heapCopy = null;
        if (materializeHeapArray) {
            heapCopy = new short[cursor];
            MemoryUtil.memShortBuffer(indicesAddr, cursor).get(heapCopy, 0, cursor);
        }
        return new Result(heapCopy, indicesAddr, cursor, middleStart, farStart, middleEnds, farEnds);
    }

    private static int simplifyNative(long base, long gridAddr, long cellsAddr, long usedAddr, long bucketsAddr,
                                      long indicesAddr, int quads, int size, int cursor, int[] ends, int stride) {
        MemoryUtil.memSet(usedAddr, 0, quads);
        for (int q = 0; q < quads; q++) {
            if (MemoryUtil.memGetByte(usedAddr + q) != 0) continue;
            int address = MemoryUtil.memGetInt(cellsAddr + (long) q * Integer.BYTES);
            int origin = -1;
            int patchSize = size;
            if (address >= 0 && MemoryUtil.memGetInt(gridAddr + (long) address * Integer.BYTES) == q) {
                int cell = address % PLANE_CELLS;
                for (; patchSize >= 2; patchSize /= 2) {
                    int candidate = address - cell + (cell / 16 / patchSize * patchSize) * 16 + cell % 16 / patchSize * patchSize;
                    if (completeNative(base, gridAddr, usedAddr, q, candidate, patchSize, stride)) {
                        origin = candidate;
                        break;
                    }
                }
            }
            ends[MemoryUtil.memGetByte(bucketsAddr + q) & 0xFF]++;
            if (origin < 0) {
                int v0 = q * 4;
                cursor = emitNative(indicesAddr, cursor, v0, v0 + 1, v0 + 2, v0 + 3);
            } else {
                int a = cornerNative(base, gridAddr, q, origin, patchSize, 0, stride);
                int b = cornerNative(base, gridAddr, q, origin, patchSize, 1, stride);
                int c = cornerNative(base, gridAddr, q, origin, patchSize, 2, stride);
                int d = cornerNative(base, gridAddr, q, origin, patchSize, 3, stride);
                cursor = emitNative(indicesAddr, cursor, a, b, c, d);
                for (int y = 0; y < patchSize; y++) {
                    for (int x = 0; x < patchSize; x++) {
                        int member = MemoryUtil.memGetInt(gridAddr + (long) (origin + y * 16 + x) * Integer.BYTES);
                        MemoryUtil.memPutByte(usedAddr + member, (byte) 1);
                    }
                }
            }
        }
        for (int i = 1; i < ends.length; i++) ends[i] += ends[i - 1];
        return cursor;
    }

    private static boolean completeNative(long base, long gridAddr, long usedAddr, int reference, int origin, int size, int stride) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int q = MemoryUtil.memGetInt(gridAddr + (long) (origin + y * 16 + x) * Integer.BYTES);
                if (q < 0 || MemoryUtil.memGetByte(usedAddr + q) != 0 || !sameMaterialNative(base, reference, q, stride)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean sameMaterialNative(long base, int a, int b, int stride) {
        int axis = flatAxisNative(base, a * 4, stride);
        long uOff = (long) ((axis + 1) % 3) * 4;
        long vOff = (long) ((axis + 2) % 3) * 4;
        long qa = base + (long) a * 4 * stride;
        long qb = base + (long) b * 4 * stride;
        float du = MemoryUtil.memGetFloat(qa + uOff) - MemoryUtil.memGetFloat(qb + uOff);
        float dv = MemoryUtil.memGetFloat(qa + vOff) - MemoryUtil.memGetFloat(qb + vOff);
        for (int i = 0; i < 4; i++) {
            long pa = qa + (long) i * stride;
            long pb = qb + (long) i * stride;
            if (MemoryUtil.memGetLong(pa + 16) != MemoryUtil.memGetLong(pb + 16)
                    || (stride == 32 && (MemoryUtil.memGetInt(pa + 28) & 0xffffff) != (MemoryUtil.memGetInt(pb + 28) & 0xffffff))
                    || MemoryUtil.memGetFloat(pa + uOff) - MemoryUtil.memGetFloat(pb + uOff) != du
                    || MemoryUtil.memGetFloat(pa + vOff) - MemoryUtil.memGetFloat(pb + vOff) != dv) return false;
        }
        return true;
    }

    private static int fractionNative(float value, int quad, long base, int axis, int stride) {
        long qPtr = base + (long) quad * 4 * stride + (long) axis * 4;
        float min = MemoryUtil.memGetFloat(qPtr);
        min = Math.min(min, MemoryUtil.memGetFloat(qPtr + stride));
        min = Math.min(min, MemoryUtil.memGetFloat(qPtr + 2L * stride));
        min = Math.min(min, MemoryUtil.memGetFloat(qPtr + 3L * stride));
        return (int) (value - min);
    }

    private static int cornerNative(long base, long gridAddr, int reference, int origin, int size, int vertex, int stride) {
        int axis = flatAxisNative(base, reference * 4, stride), u = (axis + 1) % 3, v = (axis + 2) % 3;
        long vp = base + (long) (reference * 4 + vertex) * stride;
        int highU = fractionNative(MemoryUtil.memGetFloat(vp + (long) u * 4), reference, base, u, stride);
        int highV = fractionNative(MemoryUtil.memGetFloat(vp + (long) v * 4), reference, base, v, stride);
        return MemoryUtil.memGetInt(gridAddr + (long) (origin + highV * (size - 1) * 16 + highU * (size - 1)) * Integer.BYTES) * 4 + vertex;
    }

    private static int cellNative(long base, int first, int stride, int bucket) {
        if (bucket == 3) return -1;
        int axis = bucket < 3 ? bucket : 6 - bucket;
        long axisOff = (long) axis * 4;
        long uOff = (long) ((axis + 1) % 3) * 4;
        long vOff = (long) ((axis + 2) % 3) * 4;
        long firstPtr = base + (long) first * stride;
        float plane = MemoryUtil.memGetFloat(firstPtr + axisOff);
        if (!integer(plane) || plane < 0 || plane > 16) return -1;
        float minU = 16, minV = 16, maxU = 0, maxV = 0;
        for (int i = 0; i < 4; i++) {
            long vp = firstPtr + (long) i * stride;
            float x = MemoryUtil.memGetFloat(vp + uOff), y = MemoryUtil.memGetFloat(vp + vOff);
            if (!integer(x) || !integer(y) || x < 0 || y < 0 || x > 16 || y > 16) return -1;
            minU = Math.min(minU, x); minV = Math.min(minV, y); maxU = Math.max(maxU, x); maxV = Math.max(maxV, y);
            if (!Float.isFinite(MemoryUtil.memGetFloat(vp + 16)) || !Float.isFinite(MemoryUtil.memGetFloat(vp + 20))) return -1;
        }
        if (maxU - minU != 1 || maxV - minV != 1) return -1;
        int mask = 0;
        for (int i = 0; i < 4; i++) {
            long vp = firstPtr + (long) i * stride;
            int corner = (int) (MemoryUtil.memGetFloat(vp + uOff) - minU)
                    + 2 * (int) (MemoryUtil.memGetFloat(vp + vOff) - minV);
            if ((mask & (1 << corner)) != 0) return -1;
            mask |= 1 << corner;
        }
        int facing = axis * 2 + (bucket < 3 ? 1 : 0);
        return facing * FACING_CELLS + (int) plane * PLANE_CELLS + (int) minV * 16 + (int) minU;
    }

    private static int flatAxisNative(long base, int first, int stride) {
        long fp = base + (long) first * stride;
        for (int axis = 0; axis < 3; axis++) {
            long off = (long) axis * 4;
            float plane = MemoryUtil.memGetFloat(fp + off);
            if (Float.isFinite(plane)
                    && MemoryUtil.memGetFloat(fp + stride + off) == plane
                    && MemoryUtil.memGetFloat(fp + 2L * stride + off) == plane
                    && MemoryUtil.memGetFloat(fp + 3L * stride + off) == plane) return axis;
        }
        return -1;
    }

    private static int emitNative(long indicesAddr, int cursor, int a, int b, int c, int d) {
        long p = indicesAddr + (long) cursor * Short.BYTES;
        MemoryUtil.memPutShort(p, (short) a);
        MemoryUtil.memPutShort(p + 2, (short) b);
        MemoryUtil.memPutShort(p + 4, (short) c);
        MemoryUtil.memPutShort(p + 6, (short) c);
        MemoryUtil.memPutShort(p + 8, (short) d);
        MemoryUtil.memPutShort(p + 10, (short) a);
        return cursor + 6;
    }

    /** Fallback for heap ByteBuffers in JUnit tests: copies into an off-heap temporary slice and runs the exact native algorithm. */
    private static Result buildFromHeapBuffer(ByteBuffer source, int stride, int vertexCount) {
        int bytes = vertexCount * stride;
        OffHeapTerrainScratch scratch = OffHeapTerrainScratch.current();
        try (var slice = scratch.borrowSnapshot(bytes)) {
            ByteBuffer directView = MemoryUtil.memByteBuffer(slice.address(), bytes).order(ByteOrder.nativeOrder());
            directView.put(0, source, source.position(), bytes);
            scratch.clearSorted();
            return buildNative(directView, stride, vertexCount, true);
        }
    }

    private static boolean integer(float value) { return Float.isFinite(value) && value == Math.floor(value); }
}
