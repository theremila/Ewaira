package remila.sex.cirrus.terrain;

import remila.sex.cirrus.lod.TerrainLod;
import remila.sex.cirrus.memory.OffHeapTerrainScratch;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.lwjgl.system.MemoryUtil;

/** Immutable ranges belonging to one compiled solid mesh; vertices retain their native format. */
public final class TerrainFaces {
    private static final int OTHER = 3;
    public static final int MAX_RANGES = 4;
    private static final ThreadLocal<TerrainSortBuffers> HEAP_BUFFERS = ThreadLocal.withInitial(TerrainSortBuffers::new);
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
        if (source.isDirect() && ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            return sortOffHeap(source, stride, vertexCount);
        }
        return sortHeapFallback(source, stride, vertexCount);
    }

    private static TerrainFaces sortOffHeap(ByteBuffer source, int stride, int vertexCount) {
        long base = MemoryUtil.memAddress(source);
        int quads = vertexCount / 4;
        int quadBytes = 4 * stride;
        int totalBytes = vertexCount * stride;
        OffHeapTerrainScratch scratch = OffHeapTerrainScratch.current();
        scratch.clearSorted();
        long bucketsAddr = scratch.buckets(quads);
        boolean ordered = true;
        int previousBucket = -1;
        int[] counts = new int[7];
        float[] planes = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                0, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (int q = 0; q < quads; q++) {
            long start = base + (long) q * quadBytes;
            int bucket = classifyNative(start, stride);
            if (bucket < 0) return null;
            ordered &= bucket >= previousBucket;
            previousBucket = bucket;
            MemoryUtil.memPutByte(bucketsAddr + q, (byte) bucket);
            counts[bucket]++;
            if (bucket != OTHER) {
                int axis = bucket < OTHER ? bucket : 6 - bucket;
                float plane = MemoryUtil.memGetFloat(start + (long) axis * 4);
                planes[bucket] = bucket < OTHER ? Math.min(planes[bucket], plane) : Math.max(planes[bucket], plane);
            }
        }
        if (counts[OTHER] == quads) return null;
        int[] ends = counts.clone();
        for (int i = 1; i < ends.length; i++) ends[i] += ends[i - 1];
        if (!ordered) {
            int[] cursors = new int[7];
            System.arraycopy(ends, 0, cursors, 1, 6);
            try (var snapshot = scratch.borrowSnapshot(totalBytes)) {
                long snapAddr = snapshot.address();
                MemoryUtil.memCopy(base, snapAddr, totalBytes);
                for (int q = 0; q < quads; q++) {
                    int bucket = MemoryUtil.memGetByte(bucketsAddr + q) & 0xFF;
                    int dstQuad = cursors[bucket]++;
                    MemoryUtil.memCopy(snapAddr + (long) q * quadBytes, base + (long) dstQuad * quadBytes, quadBytes);
                }
            }
            // Write final sorted bucket index per quad so LOD builder can skip re-classifying completely.
            int pos = 0;
            for (int b = 0; b < 7; b++) {
                int end = ends[b];
                if (end > pos) {
                    MemoryUtil.memSet(bucketsAddr + pos, b, end - pos);
                    pos = end;
                }
            }
        }
        scratch.recordSorted(base, stride, quads);
        return new TerrainFaces(ends, planes);
    }

    private static TerrainFaces sortHeapFallback(ByteBuffer source, int stride, int vertexCount) {
        ByteBuffer vertices = source.slice().order(ByteOrder.nativeOrder());
        int quads = vertexCount / 4;
        TerrainSortBuffers buffers = HEAP_BUFFERS.get();
        byte[] buckets = buffers.buckets(quads);
        boolean ordered = true;
        int previousBucket = -1;
        int[] counts = new int[7];
        float[] planes = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                0, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (int q = 0; q < quads; q++) {
            int start = q * 4 * stride;
            int bucket = classify(vertices, start, stride);
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

    static int classifyNative(long start, int stride) {
        for (int v = 0; v < 4; v++) {
            long vp = start + (long) v * stride;
            if (!Float.isFinite(MemoryUtil.memGetFloat(vp))
                    || !Float.isFinite(MemoryUtil.memGetFloat(vp + 4))
                    || !Float.isFinite(MemoryUtil.memGetFloat(vp + 8))) return OTHER;
        }
        for (int axis = 0; axis < 3; axis++) {
            long axisOff = (long) axis * 4;
            float plane = MemoryUtil.memGetFloat(start + axisOff);
            if (MemoryUtil.memGetFloat(start + stride + axisOff) != plane
                    || MemoryUtil.memGetFloat(start + 2L * stride + axisOff) != plane
                    || MemoryUtil.memGetFloat(start + 3L * stride + axisOff) != plane) continue;
            long aOff = (long) ((axis + 1) % 3) * 4;
            long bOff = (long) ((axis + 2) % 3) * 4;
            double a0 = MemoryUtil.memGetFloat(start + aOff), b0 = MemoryUtil.memGetFloat(start + bOff);
            double a1 = MemoryUtil.memGetFloat(start + stride + aOff) - a0;
            double b1 = MemoryUtil.memGetFloat(start + stride + bOff) - b0;
            double a2 = MemoryUtil.memGetFloat(start + 2L * stride + aOff) - a0;
            double b2 = MemoryUtil.memGetFloat(start + 2L * stride + bOff) - b0;
            double a3 = MemoryUtil.memGetFloat(start + 3L * stride + aOff) - a0;
            double b3 = MemoryUtil.memGetFloat(start + 3L * stride + bOff) - b0;
            double first = a1 * b2 - b1 * a2;
            double second = a2 * b3 - b2 * a3;
            if (first == 0 && second == 0) continue;
            if (first * second < 0) return -1;
            return first + second > 0 ? axis : 6 - axis;
        }
        return OTHER;
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

    /**
     * Exact multi-bucket directional ranges: emits 0..4 contiguous visible index ranges into {@code outRanges}
     * with zero heap allocations, skipping 100% of hidden interior face buckets.
     */
    public int ranges(int indexCount, double x, double y, double z, boolean cull, long[] outRanges) {
        if (indexCount != totalIndices) {
            outRanges[0] = pack(0, indexCount);
            return indexCount > 0 ? 1 : 0;
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            long full = fullRange();
            outRanges[0] = full;
            return indexCount(full) > 0 ? 1 : 0;
        }
        int level = hasLod() ? TerrainLod.level(x, y, z) : 0;
        TerrainFaces selected = level == 0 ? this : lodLevels[level - 1];
        int baseOffset = level == 0 ? 0 : lodStarts[level - 1];
        if (!cull || !TerrainOptimization.isEnabled()) {
            int fullCount = selected.ends[6] * 6;
            if (fullCount == 0) return 0;
            outRanges[0] = pack(baseOffset, fullCount);
            return 1;
        }
        int visible = selected.visibleMask(x, y, z);
        if (visible == 0) return 0;
        int written = 0;
        int runStart = -1;
        int runEnd = 0;
        for (int b = 0; b < 7; b++) {
            int start = b == 0 ? 0 : selected.ends[b - 1];
            int end = selected.ends[b];
            if (start == end) continue; // Empty bucket does not break a contiguous index run!
            if ((visible & (1 << b)) != 0) {
                if (runStart < 0) runStart = start;
                runEnd = end;
            } else if (runStart >= 0) {
                outRanges[written++] = pack(baseOffset + runStart * 6, (runEnd - runStart) * 6);
                runStart = -1;
            }
        }
        if (runStart >= 0) {
            outRanges[written++] = pack(baseOffset + runStart * 6, (runEnd - runStart) * 6);
        }
        return written;
    }

    /** Compact visibility signature (LOD level + exact 7-bit visible bucket mask) for fast snapshot matching. */
    public int visibilityKey(int indexCount, double x, double y, double z, boolean cull) {
        if (indexCount != totalIndices) return -1;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return 0xFF;
        int level = hasLod() ? TerrainLod.level(x, y, z) : 0;
        TerrainFaces selected = level == 0 ? this : lodLevels[level - 1];
        int mask = (cull && TerrainOptimization.isEnabled()) ? selected.visibleMask(x, y, z) : selected.nonemptyGroups;
        return (level << 8) | mask;
    }

    private int visibleMask(double x, double y, double z) {
        double margin = TerrainOptimization.margin();
        return nonemptyGroups & ((x > planes[0] - margin ? 1 : 0)
                | (y > planes[1] - margin ? 2 : 0) | (z > planes[2] - margin ? 4 : 0) | 8
                | (z < planes[4] + margin ? 16 : 0) | (y < planes[5] + margin ? 32 : 0)
                | (x < planes[6] + margin ? 64 : 0));
    }

    private long visibleRange(double x, double y, double z, boolean cull) {
        if (!cull) return fullRange();
        int visible = visibleMask(x, y, z);
        if (visible == 0) return pack(0, 0);
        int firstGroup = Integer.numberOfTrailingZeros(visible);
        int first = firstGroup == 0 ? 0 : ends[firstGroup - 1];
        int last = ends[31 - Integer.numberOfLeadingZeros(visible)];
        return pack(first * 6, (last - first) * 6);
    }

    private static long pack(int first, int count) { return ((long) first << 32) | (count & 0xffffffffL); }
    public static int firstIndex(long range) { return (int) (range >>> 32); }
    public static int indexCount(long range) { return (int) range; }
}
