package remila.sex.cirrus.occlusion;

/** Immutable occupancy captured during mesh compilation, published with that mesh's visibility set. */
public final class SectionOccluders {
    private final int x, y, z;
    private final long[] occupied;
    private SectionOccluders(int x, int y, int z, long[] occupied) {
        this.x = x; this.y = y; this.z = z; this.occupied = occupied;
    }
    public boolean contains(int x, int y, int z) {
        if ((x >> 4) != this.x || (y >> 4) != this.y || (z >> 4) != this.z) return false;
        int bit = index(x, y, z);
        return (occupied[bit >> 6] & (1L << (bit & 63))) != 0;
    }
    private static int index(int x, int y, int z) { return (x & 15) | ((z & 15) << 4) | ((y & 15) << 8); }
    public static final class Builder {
        private final int x, y, z;
        private final long[] occupied = new long[64];
        public Builder(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        public void mark(int x, int y, int z) {
            if ((x >> 4) != this.x || (y >> 4) != this.y || (z >> 4) != this.z) {
                throw new IllegalArgumentException("Occluder belongs to another section");
            }
            int bit = index(x, y, z);
            occupied[bit >> 6] |= 1L << (bit & 63);
        }
        public SectionOccluders build() { return new SectionOccluders(x, y, z, occupied.clone()); }
    }
}
