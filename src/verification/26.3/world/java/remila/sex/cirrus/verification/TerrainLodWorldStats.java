package remila.sex.cirrus.verification;

import java.util.concurrent.atomic.AtomicLong;

/** Development counters only; no production counters or per-frame allocations. */
public final class TerrainLodWorldStats {
    public static final AtomicLong fullIndices = new AtomicLong(), middleIndices = new AtomicLong(), farIndices = new AtomicLong();
    public static final AtomicLong compiled = new AtomicLong();
    public static final AtomicLong[] draws = {new AtomicLong(), new AtomicLong(), new AtomicLong()};
    public static boolean recordDraws = true;
    private TerrainLodWorldStats() {}
    public static String report() {
        return "lod_sections_built=" + compiled.get() + " near_draws=" + draws[0].get()
                + " middle_draws=" + draws[1].get() + " far_draws=" + draws[2].get() + "\n"
                + "eligible_compiled_indices_full=" + fullIndices.get() + " middle=" + middleIndices.get() + " far=" + farIndices.get() + "\n";
    }
}
