package remila.sex.cirrus.verification;

import remila.sex.cirrus.lod.TerrainLod;
import remila.sex.cirrus.terrain.TerrainBatching;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Stationary ABBA comparison after streaming; development-only, no saved settings changes. */
public final class ChunkPreparationBenchmark {
    private final long[] frames = new long[16384];
    private final StringBuilder report = new StringBuilder();
    private long start, settledSince, roundStart, previousFrame, gcCount, gcMillis;
    private int round, samples;

    public boolean tick(Minecraft client, long now) {
        if (start == 0) {
            start = now;
            TerrainLod.setEnabled(true);
            TerrainBatching.setEnabled(true);
            client.options.framerateLimit().set(260);
            client.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
            client.getSingleplayerServer().execute(() -> {
                var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.connection.teleport(-522, 110, 68, 0, 30);
            });
            TerrainLodWorldStats.recordDraws = false;
            return false;
        }
        if (roundStart == 0) {
            if (round == 0) {
                boolean settled = client.levelRenderer.hasRenderedAllSections()
                        && client.levelRenderer.sectionRenderDispatcher().isQueueEmpty();
                if (!settled) settledSince = 0;
                else if (settledSince == 0) settledSince = now;
                if (now - start < 20_000_000_000L || settledSince == 0 || now - settledSince < 5_000_000_000L) return false;
                report.append("Stationary draw-group replay ABBA, native Vulkan, no Sodium; chunk preparation and all other optimizations remain on.\n")
                        .append("Same loaded world, fixed camera (-522,110,68), yaw=0 pitch=30; 2s warm-up + 6s sampled per round.\n")
                        .append("resolution=").append(client.getWindow().getWidth()).append('x').append(client.getWindow().getHeight())
                        .append(" render_distance=").append(client.options.renderDistance().get())
                        .append(" frame_limit_option=260(unlimited) vsync=false inactivity=MINIMIZED; Gamescope headless adds compositor overhead.\n");
            }
            WorldOptimizationStats.replayAllowed = (round & 3) == 1 || (round & 3) == 2;
            roundStart = previousFrame = now;
            samples = 0;
            gcCount = gc(false); gcMillis = gc(true);
            return false;
        }
        if (Math.abs(client.player.getX() + 522) > 0.01 || Math.abs(client.player.getY() - 110) > 0.01 || Math.abs(client.player.getZ() - 68) > 0.01) {
            throw new AssertionError("Benchmark camera moved");
        }
        if (now - roundStart > 2_000_000_000L) {
            if (samples == frames.length) throw new AssertionError("Benchmark sample buffer exhausted");
            frames[samples++] = now - previousFrame;
        }
        previousFrame = now;
        if (now - roundStart < 8_000_000_000L) return false;
        if (samples < 30 || !client.isWindowActive()) throw new AssertionError("Insufficient active-window benchmark samples");
        if (round < 2) Screenshot.grab(client, false);
        Arrays.sort(frames, 0, samples);
        double mean = Arrays.stream(frames, 0, samples).average().orElseThrow();
        report.append("stationary_round=").append(round).append(" group_replay=").append(WorldOptimizationStats.replayAllowed)
                .append(" samples=").append(samples).append(" average_fps=").append(1e9 / mean)
                .append(" median_ms=").append(frames[samples / 2] / 1e6)
                .append(" p99_ms=").append(frames[(int) (samples * 0.99)] / 1e6)
                .append(" gc_count=").append(gc(false) - gcCount).append(" gc_ms=").append(gc(true) - gcMillis)
                .append(" compile_queue=").append(client.levelRenderer.sectionRenderDispatcher().getCompileQueueSize())
                .append(" compiled_all=").append(client.levelRenderer.hasRenderedAllSections()).append('\n');
        System.out.println(report.substring(report.lastIndexOf("stationary_round=")));
        round++;
        roundStart = 0;
        return round == 8;
    }

    public String report() { return report.toString(); }

    private static long gc(boolean time) {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(bean -> Math.max(0, time ? bean.getCollectionTime() : bean.getCollectionCount())).sum();
    }
}
