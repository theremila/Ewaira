package dev.evasex.ewaira.debug;

import com.sun.management.ThreadMXBean;
import dev.evasex.ewaira.ModIdentity;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.LongAdder;

/** Counters are sampled only while the user's diagnostics are enabled. */
public final class RenderDiagnostics {
    private static final long REPORT_INTERVAL = 5_000_000_000L;
    private static final int BUCKET_NANOS = 250_000;
    private static final int[] frameTimes = new int[4097];
    private static boolean enabled;
    private static long started, previous, allocatedAtStart, elapsed;
    private static int frameCount, farRadius;
    private static ThreadMXBean memory;

    public enum Metric {
        FAR_BUILDS, FAR_BUILD_US, FAR_UPLOAD_BYTES, FAR_UPLOAD_US, FAR_UPLOAD_DEFERRALS, DETAIL_UPLOAD_US, FAR_DRAWS, FAR_VERTICES,
        STATIONARY_REPLAYS, MOVING_REPLAYS, PATCHED_COMMANDS, REUSED_COMMANDS_IN_PATCHED_BATCHES, TERRAIN_PREPARE_US
    }

    private static final Metric[] metrics = Metric.values();
    private static final LongAdder[] counters = Arrays.stream(metrics).map(metric -> new LongAdder()).toArray(LongAdder[]::new);

    private RenderDiagnostics() {}

    public static boolean enabled() { return enabled; }

    public static void setEnabled(boolean value) {
        if (value == enabled) return;
        enabled = value;
        started = previous = elapsed = 0;
        frameCount = farRadius = 0;
        Arrays.fill(frameTimes, 0);
        for (LongAdder counter : counters) counter.reset();
        memory = value && ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean
                && bean.isThreadAllocatedMemoryEnabled() ? bean : null;
        ModIdentity.LOGGER.info("[Debug] {}", value ? "enabled" : "disabled");
    }

    public static void add(Metric metric, long value) { counters[metric.ordinal()].add(value); }

    public static void observeFarRadius(int chunks) { farRadius = Math.max(farRadius, chunks); }

    public static void frame() {
        long now = System.nanoTime();
        if (started == 0) {
            started = previous = now;
            allocatedAtStart = allocated();
            return;
        }
        long duration = now - previous;
        previous = now;
        elapsed += duration;
        frameTimes[(int) Math.min(frameTimes.length - 1, duration / BUCKET_NANOS)]++;
        frameCount++;
        if (now - started < REPORT_INTERVAL) return;

        long endAllocated = allocated();
        var report = new StringBuilder(String.format(Locale.ROOT,
                "[Debug] frames=%d fps=%.1f frame_p95_ms=%.2f frame_p99_ms=%.2f far_limit_chunks=%d render_thread_alloc_kib_per_frame=%s",
                frameCount, frameCount * 1e9 / elapsed, percentile(95), percentile(99), farRadius,
                endAllocated < 0 || allocatedAtStart < 0 ? "unavailable"
                        : Long.toString((endAllocated - allocatedAtStart) / (1024L * frameCount))));
        for (Metric metric : metrics) {
            long count = counters[metric.ordinal()].sumThenReset();
            report.append(' ').append(metric.name().toLowerCase(Locale.ROOT)).append('=').append(count);
        }
        ModIdentity.LOGGER.info("{}", report);
        started = now;
        allocatedAtStart = allocated();
        elapsed = 0;
        frameCount = farRadius = 0;
        Arrays.fill(frameTimes, 0);
    }

    private static double percentile(int percent) {
        int remaining = (frameCount * percent + 99) / 100;
        int bucket = 0;
        while (bucket < frameTimes.length - 1 && (remaining -= frameTimes[bucket]) > 0) bucket++;
        return (bucket + 1) * BUCKET_NANOS / 1e6;
    }

    private static long allocated() { return memory == null ? -1 : memory.getThreadAllocatedBytes(Thread.currentThread().threadId()); }
}
