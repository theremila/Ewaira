package remila.sex.cirrus.culling;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.SectionPos;

/**
 * Lock-free spatial table of compiled 16x16x16 section occupancy masks (64 longs = 512 B/section).
 * Completely solid underground sections share a single immutable constant array (0 bytes overhead).
 */
public final class SectionOccupancyMap {
    public static final long[] ALL_SOLID = new long[64];
    static { Arrays.fill(ALL_SOLID, -1L); }

    private static final ConcurrentHashMap<Long, long[]> SECTIONS = new ConcurrentHashMap<>(4096);
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final AtomicLong WORLD_EPOCH = new AtomicLong();
    private static volatile WeakReference<Object> activeWorld = new WeakReference<>(null);

    private SectionOccupancyMap() {}

    public static long generation() {
        return GENERATION.get();
    }

    public static long worldEpoch() {
        return WORLD_EPOCH.get();
    }

    public static void ensureWorld(Object world) {
        if (world != activeWorld.get()) {
            WORLD_EPOCH.incrementAndGet();
            SECTIONS.clear();
            activeWorld = new WeakReference<>(world);
            GENERATION.incrementAndGet();
        }
    }

    public static void clear() {
        WORLD_EPOCH.incrementAndGet();
        if (!SECTIONS.isEmpty()) {
            SECTIONS.clear();
            GENERATION.incrementAndGet();
        }
        activeWorld = new WeakReference<>(null);
    }

    public static void put(long sectionKey, long[] mask) {
        if (mask == null || mask.length == 0) {
            if (SECTIONS.remove(sectionKey) != null) GENERATION.incrementAndGet();
            return;
        }
        SECTIONS.put(sectionKey, mask);
        GENERATION.incrementAndGet();
    }

    /** Ignores late section-compiler completions that started before a world switch. */
    public static void putIfWorldEpoch(long expectedEpoch, long sectionKey, long[] mask) {
        if (WORLD_EPOCH.get() != expectedEpoch) return;
        put(sectionKey, mask);
        if (WORLD_EPOCH.get() != expectedEpoch) SECTIONS.remove(sectionKey, mask);
    }

    public static void removeColumn(int chunkX, int chunkZ, int minSectionY, int maxSectionY) {
        boolean removed = false;
        for (int sy = minSectionY; sy <= maxSectionY; sy++) {
            if (SECTIONS.remove(SectionPos.asLong(chunkX, sy, chunkZ)) != null) {
                removed = true;
            }
        }
        if (removed) GENERATION.incrementAndGet();
    }

    /** Fast lookup for DDA ray-marching: returns true only for confirmed full-opaque occluding blocks. */
    public static boolean isOpaque(int x, int y, int z) {
        long[] mask = SECTIONS.get(SectionPos.asLong(x >> 4, y >> 4, z >> 4));
        if (mask == null) return false;
        if (mask == ALL_SOLID) return true;
        int bit = (x & 15) | ((z & 15) << 4) | ((y & 15) << 8);
        int word = bit >>> 6;
        return word < mask.length && (mask[word] & (1L << (bit & 63))) != 0L;
    }

    public static int size() {
        return SECTIONS.size();
    }
}
