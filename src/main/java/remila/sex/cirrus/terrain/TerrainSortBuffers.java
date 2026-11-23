package remila.sex.cirrus.terrain;

/** Worker-owned scratch bytes; unusually large meshes never enlarge the retained buffers. */
final class TerrainSortBuffers {
    private static final int MAX_SNAPSHOT = 1 << 20;
    private static final int MAX_BUCKETS = 1 << 15;
    private byte[] snapshot = new byte[0];
    private byte[] buckets = new byte[0];

    byte[] snapshot(int size) {
        if (size > MAX_SNAPSHOT) return new byte[size];
        if (snapshot.length < size) snapshot = new byte[capacity(size)];
        return snapshot;
    }

    byte[] buckets(int size) {
        if (size > MAX_BUCKETS) return new byte[size];
        if (buckets.length < size) buckets = new byte[capacity(size)];
        return buckets;
    }

    private static int capacity(int size) { return size <= 1 ? 1 : Integer.highestOneBit(size - 1) << 1; }
}
