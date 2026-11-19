package dev.evasex.ewaira.worldgen;

import net.minecraft.world.level.biome.Climate;

/** One synchronous vanilla climate query per worker may borrow an array. */
public final class ClimateScratch {
    private static final ThreadLocal<ClimateScratch> BUFFERS = ThreadLocal.withInitial(ClimateScratch::new);
    public final long[] parameters = new long[7];
    public Climate.TargetPoint point;
    public Climate.DistanceMetric<?> metric;
    public boolean taken;

    private ClimateScratch() {}

    public static ClimateScratch current() { return BUFFERS.get(); }
}
