package dev.evasex.ewaira.mixin;

import dev.evasex.ewaira.config.Optimization;
import java.util.Arrays;
import dev.evasex.ewaira.integration.EwairaConfigScreen;
import dev.evasex.ewaira.integration.EwairaModMenu;
import dev.evasex.ewaira.lod.TerrainLod;
import dev.evasex.ewaira.lod.FarTerrainLod;
import dev.evasex.ewaira.startup.TexturePreparation;
import dev.evasex.ewaira.worldgen.DensityBufferReuse;
import dev.evasex.ewaira.verification.ChunkPreparationBenchmark;
import dev.evasex.ewaira.verification.TerrainCommandWorldStats;
import dev.evasex.ewaira.verification.TerrainLodWorldStats;
import dev.evasex.ewaira.verification.WorldOptimizationStats;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Isolated disposable single-player world; included only by -PverifyWorld. */
@Mixin(Minecraft.class)
abstract class WorldVerificationMixin {
    @Unique private final long ewaira$start = System.nanoTime();
    @Unique private boolean ewaira$creating;
    @Unique private EwairaConfigScreen ewaira$configScreen;
    @Unique private long ewaira$configOpened;
    @Unique private long ewaira$joined;
    @Unique private long ewaira$phaseStart;
    @Unique private long ewaira$previousFrame;
    @Unique private int ewaira$phase;
    @Unique private int ewaira$initialHeight;
    @Unique private boolean ewaira$farScreenshot;
    @Unique private long ewaira$phaseDraws;
    @Unique private final List<Long> ewaira$frames = new ArrayList<>();
    @Unique private final StringBuilder ewaira$report = new StringBuilder();
    @Unique private final ChunkPreparationBenchmark ewaira$benchmark = new ChunkPreparationBenchmark();

    @Inject(method = "runTick", at = @At("TAIL"))
    private void ewaira$worldCheck(CallbackInfo ci) throws IOException {
        Minecraft client = (Minecraft) (Object) this;
        long now = System.nanoTime();
        if (now - ewaira$start > 420_000_000_000L) {
            throw new AssertionError("Timed out creating or testing world");
        }
        if (Boolean.getBoolean("ewaira.verifyStartup")) {
            if (!ewaira$creating && client.gui.overlay() == null && client.gui.screen() instanceof TitleScreen) {
                ewaira$creating = true;
                System.out.printf("ewaira_startup_ms=%d texture_preparation=%s%n",
                        ManagementFactory.getRuntimeMXBean().getUptime(), TexturePreparation.isEnabled());
                client.stop();
            }
            return;
        }
        if (!ewaira$creating) {
            if (client.gui.overlay() != null || (ewaira$configScreen == null && !(client.gui.screen() instanceof TitleScreen))) return;
            if (ewaira$configScreen == null) {
                ewaira$configScreen = FabricLoader.getInstance().isModLoaded("modmenu")
                        ? (EwairaConfigScreen) new EwairaModMenu().getModConfigScreenFactory().create(client.gui.screen())
                        : new EwairaConfigScreen(client.gui.screen());
                client.gui.setScreen(ewaira$configScreen);
                ewaira$configOpened = now;
                return;
            }
            if (now - ewaira$configOpened < 1_000_000_000L) return;
            long checkboxes = ewaira$configScreen.children().stream().filter(child -> child instanceof Checkbox).count();
            long expected = 2 + Arrays.stream(Optimization.values()).filter(option -> option.supports("26.3")).count();
            if (checkboxes != expected) throw new AssertionError("Expected " + expected + " checkboxes, got " + checkboxes);
            for (var child : ewaira$configScreen.children()) if (child instanceof AbstractWidget widget) {
                if (widget.getX() < 0 || widget.getY() < 0 || widget.getX() + widget.getWidth() > ewaira$configScreen.width
                        || widget.getY() + widget.getHeight() > ewaira$configScreen.height) throw new AssertionError("Config widget outside screen: " + widget.getMessage());
            }
            Screenshot.grab(client, false);
            ewaira$report.append("PASS: checkbox-only settings opened on real GPU; widgets fit window.\n");
            client.gui.setScreen(null);
            ewaira$creating = true;
            client.options.pauseOnLostFocus = false;
            client.options.enableVsync().set(false);
            client.options.framerateLimit().set(60);
            client.options.renderDistance().set(Integer.getInteger("ewaira.verifyDistance", 12));
            client.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
            TerrainLod.setEnabled(true);
            DensityBufferReuse.setEnabled(!WorldOptimizationStats.DENSITY_CONTROL);
            LevelSettings settings = new LevelSettings("Ewaira verification", GameType.CREATIVE,
                    new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false),
                    true, WorldDataConfiguration.DEFAULT);
            client.createWorldOpenFlows().createFreshLevel("Ewaira-test-" + System.currentTimeMillis(),
                    settings, new WorldOptions(1234, true, false),
                    WorldPresets::createNormalWorldDimensions, null);
            return;
        }
        if (client.player == null || client.getConnection() == null) return;
        if (WorldOptimizationStats.REPLAY_ONLY) {
            if (ewaira$benchmark.tick(client, now)) {
                Files.writeString(Path.of("ewaira-world-verification.txt"), ewaira$benchmark.report()
                        + "stable_command_upload_bytes=" + TerrainCommandWorldStats.reusedBytes() + "\n"
                        + "draw_group_replays=" + WorldOptimizationStats.drawGroupHits + "\n");
                client.stop();
            }
            return;
        }
        if (ewaira$phase == 6) {
            if (ewaira$benchmark.tick(client, now)) {
                ewaira$report.append(ewaira$benchmark.report())
                        .append("stable_command_upload_bytes=").append(TerrainCommandWorldStats.reusedBytes()).append("\n");
                if (TerrainCommandWorldStats.reusedBytes() == 0) throw new AssertionError("No stable command batches uploaded");
                ewaira$report.append(WorldOptimizationStats.report());
                Files.writeString(Path.of("ewaira-world-verification.txt"), ewaira$report);
                client.stop();
            }
            return;
        }
        if (ewaira$joined == 0) {
            ewaira$joined = now;
            client.gui.setScreen(null);
            ewaira$report.append("PASS: integrated server joined; automatic modules active.\n")
                    .append("Streaming teleports, seed 1234, configured render distance; automatic LOD thresholds, no Sodium; focus/GC make these exploratory timings, not FPS evidence.\n");
            return;
        }
        if (now - ewaira$joined < 20_000_000_000L) return;
        if (ewaira$phase == 0 && ewaira$phaseStart == 0) {
            String mode = WorldOptimizationStats.DENSITY_CONTROL ? "off" : "on";
            Files.writeString(Path.of("surface-states-" + mode + ".txt"), WorldOptimizationStats.surfaceStates());
            if (WorldOptimizationStats.DENSITY_CONTROL) {
                Files.writeString(Path.of("ewaira-world-verification.txt"), "Density control: generated 25 surface samples with native buffers.\n");
                client.stop();
                return;
            }
        }
        if (ewaira$phaseStart == 0) {
            ewaira$phaseStart = now;
            if (ewaira$phase == 4) {
                ewaira$initialHeight = client.getWindow().getHeight();
                client.getWindow().setWindowed(768, 400);
            }
            if (ewaira$phase == 5) {
                ewaira$report.append("framebuffer_resize_before=").append(ewaira$initialHeight)
                        .append(" after=").append(client.getWindow().getHeight()).append('\n');
                client.getWindow().setWindowed(854, 480);
                ewaira$phaseDraws = WorldOptimizationStats.farTerrainDraws;
            }
            ewaira$previousFrame = now;
            TerrainLod.setEnabled(ewaira$phase % 2 == 1);
            int phase = ewaira$phase;
            double height = ewaira$height(phase);
            var server = client.getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.connection.teleport(phase * 96.0, height, 0, phase == 5 ? 90 : 0, phase == 5 ? 17 : 30);
            });
            return;
        }
        if (now - ewaira$phaseStart > 1_000_000_000L) {
            ewaira$frames.add(now - ewaira$previousFrame);
        }
        ewaira$previousFrame = now;
        if (ewaira$phase == 5 && !ewaira$farScreenshot && now - ewaira$phaseStart >= 5_000_000_000L
                && WorldOptimizationStats.farTerrainDraws > ewaira$phaseDraws) {
            Screenshot.grab(client.gameDirectory, "distant-terrain-on.png", client.gameRenderer.mainRenderTarget(), 1, ignored -> {});
            ewaira$farScreenshot = true;
            FarTerrainLod.setEnabled(false);
        }
        if (now - ewaira$phaseStart < 6_000_000_000L) return;
        if (ewaira$phase == 5 && ewaira$farScreenshot) {
            Screenshot.grab(client.gameDirectory, "distant-terrain-off.png", client.gameRenderer.mainRenderTarget(), 1, ignored -> {});
            FarTerrainLod.setEnabled(true);
        }
        if (Math.abs(client.player.getX() - ewaira$phase * 96.0) > 8 || Math.abs(client.player.getY() - ewaira$height(ewaira$phase)) > 1) {
            throw new AssertionError("Streaming teleport was not acknowledged by the client");
        }
        long[] samples = ewaira$frames.stream().mapToLong(Long::longValue).sorted().toArray();
        double mean = java.util.Arrays.stream(samples).average().orElseThrow();
        ewaira$report.append("round=").append(ewaira$phase)
                .append(" enabled=").append(TerrainLod.isEnabled())
                .append(" teleport_acknowledged=true")
                .append(" focused=").append(client.isWindowActive())
                .append(" samples=").append(samples.length)
                .append(" average_fps=").append(1e9 / mean)
                .append(" p99_ms=").append(samples[(int) (samples.length * 0.99)] / 1e6)
                .append("\n");
        if (ewaira$phase == 3) Screenshot.grab(client, false);
        ewaira$phase++;
        ewaira$frames.clear();
        ewaira$phaseStart = 0;
        if (ewaira$phase == 6) {
            ewaira$report.append(TerrainLodWorldStats.report());
            if (TerrainLodWorldStats.compiled.get() == 0 || TerrainLodWorldStats.draws[1].get() == 0 || TerrainLodWorldStats.draws[2].get() == 0) {
                throw new AssertionError("Missing world LOD compilation/draws: " + TerrainLodWorldStats.report());
            }
        }
    }

    @Unique private static double ewaira$height(int phase) {
        return switch (phase % 3) { case 0 -> 96; case 1 -> 112; default -> 180; };
    }
}
