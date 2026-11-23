package remila.sex.cirrus.verification;

import remila.sex.cirrus.worldgen.BiomeSampling;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.BiomeManager;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

public final class BiomeSamplingVerification {
    private static volatile int sink;
    public static String verify() throws ReflectiveOperationException, InterruptedException {
        Constructor<?> constructor = BiomeManager.class.getConstructors()[0];
        int[] selected = new int[3];
        Object source = Proxy.newProxyInstance(BiomeManager.class.getClassLoader(), new Class<?>[]{constructor.getParameterTypes()[0]}, (proxy, method, args) -> {
            for (int i = 0; i < 3; i++) selected[i] = (int) args[i];
            return null;
        });
        Random random = new Random(42);
        try {
            for (long seed : new long[]{0, -1, Long.MIN_VALUE, 1234567}) {
                BiomeManager manager = (BiomeManager) constructor.newInstance(source, seed);
                for (int i = 0; i < 12000; i++) {
                    BlockPos pos = i < 4096 ? new BlockPos(i & 15, i >> 8, i >> 4 & 15) : new BlockPos(random.nextInt(), random.nextInt(), random.nextInt());
                    BiomeSampling.setEnabled(false); manager.getBiome(pos);
                    int x = selected[0], y = selected[1], z = selected[2];
                    BiomeSampling.setEnabled(true); manager.getBiome(pos);
                    if (selected[0] != x || selected[1] != y || selected[2] != z) throw new AssertionError("Biome mismatch: " + pos + " seed=" + seed);
                }
            }
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread[] threads = new Thread[4];
            for (int t = 0; t < threads.length; t++) {
                final long seed = t;
                threads[t] = new Thread(() -> {
                    try { for (int i = 0; i < 20000; i++) {
                        int a = BiomeSampling.corner(seed, i & 127, i >> 7, -i);
                        BiomeSampling.corner(seed + 1, i, -i, i);
                        if (a != BiomeSampling.corner(seed, i & 127, i >> 7, -i)) throw new AssertionError("Cross-seed contamination");
                    }} catch (Throwable problem) { failure.compareAndSet(null, problem); }
                });
                threads[t].start();
            }
            for (Thread thread : threads) thread.join();
            if (failure.get() != null) throw new AssertionError(failure.get());
            BiomeManager manager = (BiomeManager) constructor.newInstance(source, 1234L);
            long[] vanilla = new long[9], cached = new long[9];
            for (int round = 0; round < 15; round++) {
                long a, b;
                if ((round & 1) == 0) { a = run(manager, false); b = run(manager, true); }
                else { b = run(manager, true); a = run(manager, false); }
                if (round >= 6) { vanilla[round - 6] = a; cached[round - 6] = b; }
            }
            Arrays.sort(vanilla); Arrays.sort(cached);
            return "PASS: 48,000 native biome selections match vanilla, extreme coordinates and seeds; four workers isolated.\nbiome_200000_native_queries_ns: vanilla=" + vanilla[4] + ", cached=" + cached[4] + "\nSynthetic biome queries with proxy source; not full worldgen throughput or FPS.\n";
        } finally { BiomeSampling.setEnabled(true); }
    }
    private static long run(BiomeManager manager, boolean enabled) {
        BiomeSampling.setEnabled(enabled);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        long start = System.nanoTime();
        for (int i = 0; i < 200000; i++) {
            pos.set(i & 31, i >> 10 & 63, i >> 5 & 31);
            manager.getBiome(pos);
        }
        sink++;
        return System.nanoTime() - start;
    }
}
