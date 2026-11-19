package dev.evasex.ewaira.verification;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import java.util.concurrent.atomic.AtomicLong;

/** Counters for an isolated verification client. */
public final class WorldOptimizationStats {
    public static final AtomicLong densityLeases = new AtomicLong();
    public static final AtomicLong densityAllocations = new AtomicLong();
    public static final AtomicInteger densityCapacity = new AtomicInteger();
    public static final boolean DENSITY_CONTROL = "1".equals(System.getenv("EWAIRA_VERIFY_DENSITY_CONTROL"));
    public static final boolean REPLAY_ONLY = "1".equals(System.getenv("EWAIRA_VERIFY_REPLAY_ONLY"));
    public static boolean replayAllowed = true;
    public static long drawGroupHits;
    public static long farTerrainDraws;
    public static int farTerrainMaxVertices;

    private static final ConcurrentSkipListMap<String, Long> surfaces = new ConcurrentSkipListMap<>();

    private WorldOptimizationStats() {}

    public static void recordSurface(ChunkAccess chunk) {
        var pos = chunk.getPos();
        if (pos.x() < -35 || pos.x() > -31 || pos.z() < 2 || pos.z() > 6 || chunk.getMinY() != -64) return;
        long hash = 0xcbf29ce484222325L;
        for (var section : chunk.getSections()) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        hash ^= Block.getId(section.getBlockState(x, y, z));
                        hash *= 0x100000001b3L;
                    }
                }
            }
        }
        surfaces.put(pos.x() + "," + pos.z(), hash);
    }

    public static String surfaceStates() {
        if (surfaces.size() != 25) throw new AssertionError("Expected 25 generated surfaces, got " + surfaces.size());
        var result = new StringBuilder();
        surfaces.forEach((position, hash) -> result.append(position).append(' ').append(Long.toHexString(hash)).append('\n'));
        return result.toString();
    }

    public static String report() {
        if (densityLeases.get() <= densityAllocations.get() || drawGroupHits == 0 || farTerrainDraws == 0) {
            throw new AssertionError("Expected density reuse, draw group replay and a distant terrain draw: " + counters());
        }
        return counters();
    }

    private static String counters() {
        return "density_leases=" + densityLeases.get() + " density_new_buffers=" + densityAllocations.get()
                + " density_max_floats=" + densityCapacity.get() + "\n"
                + "draw_group_replays=" + drawGroupHits + "\n"
                + "far_terrain_draws=" + farTerrainDraws + " far_terrain_max_vertices=" + farTerrainMaxVertices + "\n";
    }
}
