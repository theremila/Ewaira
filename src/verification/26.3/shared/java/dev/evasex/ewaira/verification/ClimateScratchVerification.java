package dev.evasex.ewaira.verification;

import com.mojang.datafixers.util.Pair;
import com.sun.management.ThreadMXBean;
import dev.evasex.ewaira.worldgen.BiomeSampling;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Random;
import net.minecraft.world.level.biome.Climate;

public final class ClimateScratchVerification {
    private ClimateScratchVerification() {}

    public static String verify() {
        var points = new Random(42);
        var parameters = new ArrayList<Pair<Climate.ParameterPoint, Integer>>();
        for (int i = 0; i < 192; i++) {
            parameters.add(Pair.of(Climate.parameters(next(points), next(points), next(points), next(points),
                    next(points), next(points), i % 3 * 0.1f), i));
        }
        var normal = new Climate.ParameterList<>(parameters);
        var optimized = new Climate.ParameterList<>(parameters);
        var queries = new Climate.TargetPoint[8000];
        for (int i = 0; i < queries.length; i++)
            queries[i] = Climate.target(next(points), next(points), next(points), next(points), next(points), next(points));
        int[] expected = new int[queries.length];
        boolean previous = BiomeSampling.isEnabled();
        try {
            for (int run = 0; run < 6; run++) {
                BiomeSampling.setEnabled(false);
                for (int i = 0; i < queries.length; i++) expected[i] = normal.findValue(queries[i]);
                BiomeSampling.setEnabled(true);
                for (int i = 0; i < queries.length; i++)
                    if (optimized.findValue(queries[i]) != expected[i]) throw new AssertionError("Climate sample differed " + i);
            }
            var measurements = new long[2];
            var memory = ManagementFactory.getThreadMXBean();
            if (memory instanceof ThreadMXBean allocated && allocated.isThreadAllocatedMemoryEnabled()) {
                long id = Thread.currentThread().threadId();
                for (int variant = 0; variant < 2; variant++) {
                    BiomeSampling.setEnabled(variant == 1);
                    long start = allocated.getThreadAllocatedBytes(id);
                    for (int i = 0; i < queries.length; i++) {
                        int actual = optimized.findValue(queries[i]);
                        if (actual != expected[i]) throw new AssertionError("Climate sample differed " + i);
                    }
                    measurements[variant] = (allocated.getThreadAllocatedBytes(id) - start) / queries.length;
                }
            }
            return "PASS: 48,000 native climate index lookups preserve results and tie ordering.\n"
                    + "climate_bytes_per_query: vanilla=" + measurements[0] + " reuse=" + measurements[1] + "\n";
        } finally {
            BiomeSampling.setEnabled(previous);
        }
    }

    private static float next(Random random) { return random.nextFloat() * 2 - 1; }
}
