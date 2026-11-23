package remila.sex.cirrus.memory;

import org.lwjgl.system.MemoryUtil;

/**
 * Thread-confined off-heap scratch buffers for chunk terrain sorting and LOD index construction.
 * Eliminates large per-worker heap arrays and lets LOD building reuse the bucket classification
 * already computed during directional face sorting.
 */
public final class OffHeapTerrainScratch {
    private static final long MAX_RETAINED_SNAPSHOT = 1L << 20; // 1 MiB
    private static final long MAX_RETAINED_BUCKETS = 1L << 15;  // 32 KiB
    private static final long MAX_RETAINED_LOD = 1L << 20;      // 1 MiB
    private static final int LOD_GRID_INTS = 6 * 17 * 16 * 16;  // 26,112 cells

    private static final ThreadLocal<OffHeapTerrainScratch> LOCAL =
            ThreadLocal.withInitial(OffHeapTerrainScratch::new);

    private final NativeArena snapshotArena = new NativeArena(0, MAX_RETAINED_SNAPSHOT);
    private final NativeArena bucketsArena = new NativeArena(0, MAX_RETAINED_BUCKETS);
    private final NativeArena lodGridArena = new NativeArena((long) LOD_GRID_INTS * Integer.BYTES,
            (long) LOD_GRID_INTS * Integer.BYTES);
    private final NativeArena lodWorkArena = new NativeArena(0, MAX_RETAINED_LOD);
    private final NativeArena lodIndicesArena = new NativeArena(0, MAX_RETAINED_LOD);
    private final NativeArena visibilityQueueArena = new NativeArena(0, 4096L * Integer.BYTES);

    private long lastSortedAddress;
    private int lastSortedQuads;
    private int lastSortedStride;

    private OffHeapTerrainScratch() {}

    public static OffHeapTerrainScratch current() {
        return LOCAL.get();
    }

    public long buckets(int quads) {
        return bucketsArena.ensure(quads);
    }

    /** Allocates a temporary native snapshot; oversized chunks use an ephemeral allocation freed on close. */
    public NativeSlice borrowSnapshot(int bytes) {
        if (bytes <= MAX_RETAINED_SNAPSHOT) {
            return new NativeSlice(snapshotArena.ensure(bytes), false);
        }
        long temp = MemoryUtil.nmemAlloc(bytes);
        if (temp == 0L) throw new OutOfMemoryError("Failed to allocate " + bytes + " off-heap snapshot bytes");
        return new NativeSlice(temp, true);
    }

    public void recordSorted(long vertexAddress, int stride, int quads) {
        lastSortedAddress = vertexAddress;
        lastSortedStride = stride;
        lastSortedQuads = quads;
    }

    public void clearSorted() {
        lastSortedAddress = 0L;
        lastSortedStride = 0;
        lastSortedQuads = 0;
    }

    public long matchingSortedBuckets(long vertexAddress, int stride, int quads) {
        if (vertexAddress != 0L && lastSortedAddress == vertexAddress
                && lastSortedStride == stride && lastSortedQuads == quads
                && bucketsArena.capacity() >= quads) {
            return bucketsArena.address();
        }
        return 0L;
    }

    public long lodGridCleared() {
        long bytes = (long) LOD_GRID_INTS * Integer.BYTES;
        long addr = lodGridArena.ensure(bytes);
        // -1 == 0xFFFFFFFF in two's complement, so a single native memset clears all 26,112 ints.
        MemoryUtil.memSet(addr, 0xFF, bytes);
        return addr;
    }

    /** Returns a packed off-heap work buffer holding [cellsForQuad: int[quads], used: byte[quads], buckets: byte[quads]]. */
    public long lodWork(int quads) {
        long bytes = (long) quads * (Integer.BYTES + 2L);
        return lodWorkArena.ensure(bytes);
    }

    public long lodIndices(int shortCount) {
        return lodIndicesArena.ensure((long) shortCount * Short.BYTES);
    }

    public long visibilityQueue() {
        return visibilityQueueArena.ensure(4096L * Integer.BYTES);
    }

    public record NativeSlice(long address, boolean ephemeral) implements AutoCloseable {
        @Override
        public void close() {
            if (ephemeral && address != 0L) {
                MemoryUtil.nmemFree(address);
            }
        }
    }
}
