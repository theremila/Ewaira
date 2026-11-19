package dev.evasex.ewaira.terrain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Index-only planar LOD: keep native vertices and select the outer corners of complete patches. */
public final class TerrainLodIndices {
    private static final int MAX_QUADS = (1 << Short.SIZE) / 4;
    private static final int PLANE_CELLS = 16 * 16;
    private static final int FACING_CELLS = 17 * PLANE_CELLS;
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private TerrainLodIndices() {}

    /** Scratch indices are valid only until the next build on this thread; upload/copy immediately. */
    public record Result(short[] indices, int count, int middleStart, int farStart, int[] middleEnds, int[] farEnds) {}

    public static Result build(ByteBuffer source, int stride, int vertexCount) {
        if ((stride != 28 && stride != 32) || vertexCount <= 0 || vertexCount % 4 != 0 || vertexCount / 4 > MAX_QUADS
                || (long) stride * vertexCount > source.remaining()) return null;
        ByteBuffer vertices = source.slice().order(ByteOrder.nativeOrder());
        int quads = vertexCount / 4;
        Scratch scratch = SCRATCH.get();
        scratch.prepare(quads);
        for (int q = 0; q < quads; q++) {
            int bucket = TerrainFaces.classify(vertices, q * 4 * stride, stride);
            if (bucket < 0) return null;
            scratch.buckets[q] = (byte) bucket;
            int address = cell(vertices, q * 4, stride, bucket);
            scratch.cellsForQuad[q] = address;
            if (address >= 0) scratch.grid[address] = scratch.grid[address] == -1 ? q : -2;
        }
        int cursor = 0;
        for (int q = 0; q < quads; q++) cursor = emit(scratch.indices, cursor, q * 4, q * 4 + 1, q * 4 + 2, q * 4 + 3);
        int middleStart = cursor;
        int[] middleEnds = new int[7];
        cursor = simplify(vertices, scratch, quads, 2, cursor, middleEnds, stride);
        int farStart = cursor;
        int[] farEnds = new int[7];
        cursor = simplify(vertices, scratch, quads, 4, cursor, farEnds, stride);
        // Avoid paying for a custom index buffer on sections with negligible planar reduction.
        if ((farStart - middleStart) * 8L > middleStart * 7L) return null;
        return new Result(scratch.indices, cursor, middleStart, farStart, middleEnds, farEnds);
    }

    private static int simplify(ByteBuffer vertices, Scratch scratch, int quads, int size, int cursor, int[] ends, int stride) {
        Arrays.fill(scratch.used, 0, quads, false);
        for (int q = 0; q < quads; q++) {
            if (scratch.used[q]) continue;
            int address = scratch.cellsForQuad[q];
            int origin = -1;
            int patchSize = size;
            if (address >= 0 && scratch.grid[address] == q) {
                int cell = address % PLANE_CELLS;
                for (; patchSize >= 2; patchSize /= 2) {
                    int candidate = address - cell + (cell / 16 / patchSize * patchSize) * 16 + cell % 16 / patchSize * patchSize;
                    if (complete(vertices, scratch, q, candidate, patchSize, stride)) {
                        origin = candidate;
                        break;
                    }
                }
            }
            ends[scratch.buckets[q]]++;
            if (origin < 0) {
                cursor = emit(scratch.indices, cursor, q * 4, q * 4 + 1, q * 4 + 2, q * 4 + 3);
            } else {
                int a = corner(vertices, scratch, q, origin, patchSize, 0, stride);
                int b = corner(vertices, scratch, q, origin, patchSize, 1, stride);
                int c = corner(vertices, scratch, q, origin, patchSize, 2, stride);
                int d = corner(vertices, scratch, q, origin, patchSize, 3, stride);
                cursor = emit(scratch.indices, cursor, a, b, c, d);
                for (int y = 0; y < patchSize; y++) for (int x = 0; x < patchSize; x++) scratch.used[scratch.grid[origin + y * 16 + x]] = true;
            }
        }
        for (int i = 1; i < ends.length; i++) ends[i] += ends[i - 1];
        return cursor;
    }

    private static boolean complete(ByteBuffer vertices, Scratch scratch, int reference, int origin, int size, int stride) {
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            int q = scratch.grid[origin + y * 16 + x];
            if (q < 0 || scratch.used[q] || !sameMaterial(vertices, reference, q, stride)) return false;
        }
        return true;
    }

    private static boolean sameMaterial(ByteBuffer vertices, int a, int b, int stride) {
        int axis = flatAxis(vertices, a * 4, stride);
        int u = (axis + 1) % 3, v = (axis + 2) % 3;
        float du = vertices.getFloat(a * 4 * stride + u * 4) - vertices.getFloat(b * 4 * stride + u * 4);
        float dv = vertices.getFloat(a * 4 * stride + v * 4) - vertices.getFloat(b * 4 * stride + v * 4);
        for (int i = 0; i < 4; i++) {
            int pa = (a * 4 + i) * stride, pb = (b * 4 + i) * stride;
            if (vertices.getLong(pa + 16) != vertices.getLong(pb + 16)
                    || (stride == 32 && (vertices.getInt(pa + 28) & 0xffffff) != (vertices.getInt(pb + 28) & 0xffffff))
                    || vertices.getFloat(pa + u * 4) - vertices.getFloat(pb + u * 4) != du
                    || vertices.getFloat(pa + v * 4) - vertices.getFloat(pb + v * 4) != dv) return false;
        }
        return true;
    }

    private static int fraction(float value, int quad, ByteBuffer vertices, int axis, int stride) {
        float min = Float.POSITIVE_INFINITY;
        for (int i = 0; i < 4; i++) min = Math.min(min, vertices.getFloat((quad * 4 + i) * stride + axis * 4));
        return (int) (value - min);
    }

    private static int corner(ByteBuffer vertices, Scratch scratch, int reference, int origin, int size, int vertex, int stride) {
        int axis = flatAxis(vertices, reference * 4, stride), u = (axis + 1) % 3, v = (axis + 2) % 3;
        int highU = fraction(vertices.getFloat((reference * 4 + vertex) * stride + u * 4), reference, vertices, u, stride);
        int highV = fraction(vertices.getFloat((reference * 4 + vertex) * stride + v * 4), reference, vertices, v, stride);
        return scratch.grid[origin + highV * (size - 1) * 16 + highU * (size - 1)] * 4 + vertex;
    }

    private static int cell(ByteBuffer vertices, int first, int stride, int bucket) {
        if (bucket == 3) return -1;
        int axis = bucket < 3 ? bucket : 6 - bucket;
        int u = (axis + 1) % 3, v = (axis + 2) % 3;
        float minU = 16, minV = 16, maxU = 0, maxV = 0;
        float plane = vertices.getFloat(first * stride + axis * 4);
        if (!integer(plane) || plane < 0 || plane > 16) return -1;
        for (int i = 0; i < 4; i++) {
            float x = vertices.getFloat((first + i) * stride + u * 4), y = vertices.getFloat((first + i) * stride + v * 4);
            if (!integer(x) || !integer(y) || x < 0 || y < 0 || x > 16 || y > 16) return -1;
            minU = Math.min(minU, x); minV = Math.min(minV, y); maxU = Math.max(maxU, x); maxV = Math.max(maxV, y);
            if (!Float.isFinite(vertices.getFloat((first + i) * stride + 16)) || !Float.isFinite(vertices.getFloat((first + i) * stride + 20))) return -1;
        }
        if (maxU - minU != 1 || maxV - minV != 1) return -1;
        int mask = 0;
        for (int i = 0; i < 4; i++) {
            int corner = (int) (vertices.getFloat((first + i) * stride + u * 4) - minU)
                    + 2 * (int) (vertices.getFloat((first + i) * stride + v * 4) - minV);
            if ((mask & (1 << corner)) != 0) return -1;
            mask |= 1 << corner;
        }
        int facing = axis * 2 + (bucket < 3 ? 1 : 0);
        return facing * FACING_CELLS + (int) plane * PLANE_CELLS + (int) minV * 16 + (int) minU;
    }

    private static int flatAxis(ByteBuffer vertices, int first, int stride) {
        for (int axis = 0; axis < 3; axis++) {
            float plane = vertices.getFloat(first * stride + axis * 4);
            if (Float.isFinite(plane) && vertices.getFloat((first + 1) * stride + axis * 4) == plane
                    && vertices.getFloat((first + 2) * stride + axis * 4) == plane
                    && vertices.getFloat((first + 3) * stride + axis * 4) == plane) return axis;
        }
        return -1;
    }

    private static boolean integer(float value) { return Float.isFinite(value) && value == Math.floor(value); }
    private static int emit(short[] indices, int cursor, int a, int b, int c, int d) {
        indices[cursor++] = (short) a; indices[cursor++] = (short) b; indices[cursor++] = (short) c;
        indices[cursor++] = (short) c; indices[cursor++] = (short) d; indices[cursor++] = (short) a;
        return cursor;
    }

    private static final class Scratch {
        final int[] grid = new int[6 * FACING_CELLS];
        int[] cellsForQuad = new int[0];
        short[] indices = new short[0];
        byte[] buckets = new byte[0];
        boolean[] used = new boolean[0];
        void prepare(int quads) {
            Arrays.fill(grid, -1);
            if (cellsForQuad.length < quads) {
                int capacity = Math.min(MAX_QUADS, Integer.highestOneBit(quads - 1) << 1);
                capacity = Math.max(1, capacity);
                cellsForQuad = new int[capacity];
                used = new boolean[capacity];
                buckets = new byte[capacity];
                indices = new short[capacity * 18];
            }
        }
    }
}
