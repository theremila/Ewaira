package dev.evasex.ewaira.terrain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import dev.evasex.ewaira.lod.TerrainLod;

/** Immutable ranges belonging to one compiled solid mesh; vertices retain their native format. */
public final class TerrainFaces {
    private static final int OTHER = 3;
    private static final ThreadLocal<TerrainSortBuffers> BUFFERS = ThreadLocal.withInitial(TerrainSortBuffers::new);
    private final int[] ends;
    private final float[] planes;
    private final int nonemptyGroups;
    private TerrainFaces[] lodLevels;
    private int[] lodStarts;
    private int totalIndices;

    private TerrainFaces(int[] ends, float[] planes) {
        this.ends = ends;
        this.planes = planes;
        this.totalIndices = ends[6] * 6;
        int mask = 0, previous = 0;
        for (int i = 0; i < ends.length; i++) {
            if (ends[i] != previous) mask |= 1 << i;
            previous = ends[i];
        }
        nonemptyGroups = mask;
    }

    public static TerrainFaces sort(ByteBuffer source, int stride, int vertexCount) {
        if (stride < 12 || vertexCount <= 0 || vertexCount % 4 != 0 || source.isReadOnly()
                || (long) vertexCount * stride > source.remaining()) return null;
        ByteBuffer vertices = source.slice().order(ByteOrder.nativeOrder());
        int quads = vertexCount / 4;
        TerrainSortBuffers buffers = BUFFERS.get();
        byte[] buckets = buffers.buckets(quads);
        boolean ordered = true;
        int previousBucket = -1;
        int[] counts = new int[7];
        float[] planes = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                0, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (int q = 0; q < quads; q++) {
            int start = q * 4 * stride;
            int bucket = classify(vertices, start, stride);
            // Oppositely wound triangles can overlap an ordinary face; preserve the whole mesh's order.
            if (bucket < 0) return null;
            ordered &= bucket >= previousBucket;
            previousBucket = bucket;
            buckets[q] = (byte) bucket;
            counts[bucket]++;
            if (bucket != OTHER) {
                int axis = bucket < OTHER ? bucket : 6 - bucket;
                float plane = vertices.getFloat(start + axis * 4);
                planes[bucket] = bucket < OTHER ? Math.min(planes[bucket], plane) : Math.max(planes[bucket], plane);
            }
        }
        if (counts[OTHER] == quads) return null;
        int[] ends = counts.clone();
        for (int i = 1; i < ends.length; i++) ends[i] += ends[i - 1];
        if (ordered) return new TerrainFaces(ends, planes);
        int[] cursors = new int[7];
        System.arraycopy(ends, 0, cursors, 1, 6);
        byte[] snapshot = buffers.snapshot(vertexCount * stride);
        vertices.get(0, snapshot, 0, vertexCount * stride);
        for (int q = 0; q < quads; q++) {
            int destination = cursors[buckets[q]]++ * 4 * stride;
            vertices.put(destination, snapshot, q * 4 * stride, 4 * stride);
        }
        return new TerrainFaces(ends, planes);
    }

    static int classify(ByteBuffer vertices, int start, int stride) {
        for (int v = 0; v < 4; v++) {
            for (int axis = 0; axis < 3; axis++) {
                if (!Float.isFinite(vertices.getFloat(start + v * stride + axis * 4))) return OTHER;
            }
        }
        for (int axis = 0; axis < 3; axis++) {
            float plane = vertices.getFloat(start + axis * 4);
            boolean flat = true;
            for (int v = 1; v < 4; v++) flat &= vertices.getFloat(start + v * stride + axis * 4) == plane;
            if (!flat) continue;
            int a = (axis + 1) % 3, b = (axis + 2) % 3;
            double a0 = vertices.getFloat(start + a * 4), b0 = vertices.getFloat(start + b * 4);
            double a1 = vertices.getFloat(start + stride + a * 4) - a0;
            double b1 = vertices.getFloat(start + stride + b * 4) - b0;
            double a2 = vertices.getFloat(start + stride * 2 + a * 4) - a0;
            double b2 = vertices.getFloat(start + stride * 2 + b * 4) - b0;
            double a3 = vertices.getFloat(start + stride * 3 + a * 4) - a0;
            double b3 = vertices.getFloat(start + stride * 3 + b * 4) - b0;
            double first = a1 * b2 - b1 * a2;
            double second = a2 * b3 - b2 * a3;
            if (first == 0 && second == 0) continue;
            if (first * second < 0) return -1;
            return first + second > 0 ? axis : 6 - axis;
        }
        return OTHER;
    }

    public boolean hasLod() { return lodLevels != null; }
    public long fullRange() { return pack(0, ends[6] * 6); }

    public void attachLod(TerrainLodIndices.Result indices) {
        lodLevels = new TerrainFaces[]{new TerrainFaces(indices.middleEnds(), planes), new TerrainFaces(indices.farEnds(), planes)};
        lodStarts = new int[]{indices.middleStart(), indices.farStart()};
        totalIndices = indices.count();
    }

    public long range(int indexCount, double x, double y, double z) { return range(indexCount, x, y, z, true); }

    public long range(int indexCount, double x, double y, double z, boolean cull) {
        if (indexCount != totalIndices) return pack(0, indexCount);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return fullRange();
        int level = hasLod() ? TerrainLod.level(x, y, z) : 0;
        TerrainFaces selected = level == 0 ? this : lodLevels[level - 1];
        long range = selected.visibleRange(x, y, z, cull && TerrainOptimization.isEnabled());
        return pack(firstIndex(range) + (level == 0 ? 0 : lodStarts[level - 1]), indexCount(range));
    }

    private long visibleRange(double x, double y, double z, boolean cull) {
        if (!cull) return fullRange();
        double margin = TerrainOptimization.margin();
        int visible = nonemptyGroups & ((x > planes[0] - margin ? 1 : 0)
                | (y > planes[1] - margin ? 2 : 0) | (z > planes[2] - margin ? 4 : 0) | 8
                | (z < planes[4] + margin ? 16 : 0) | (y < planes[5] + margin ? 32 : 0)
                | (x < planes[6] + margin ? 64 : 0));
        if (visible == 0) return pack(0, 0);
        int firstGroup = Integer.numberOfTrailingZeros(visible);
        int first = firstGroup == 0 ? 0 : ends[firstGroup - 1];
        int last = ends[31 - Integer.numberOfLeadingZeros(visible)];
        // One enclosing range preserves the native draw count, including any hidden groups between its ends.
        return pack(first * 6, (last - first) * 6);
    }

    private static long pack(int first, int count) { return ((long) first << 32) | (count & 0xffffffffL); }
    public static int firstIndex(long range) { return (int) (range >>> 32); }
    public static int indexCount(long range) { return (int) range; }
}
