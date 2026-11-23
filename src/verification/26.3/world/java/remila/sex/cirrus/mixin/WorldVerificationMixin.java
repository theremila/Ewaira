package remila.sex.cirrus.mixin;

import remila.sex.cirrus.config.Optimization;
import java.util.Arrays;
import remila.sex.cirrus.integration.CirrusConfigScreen;
import remila.sex.cirrus.integration.CirrusModMenu;
import remila.sex.cirrus.lod.TerrainLod;
import remila.sex.cirrus.lod.FarTerrainLod;
import remila.sex.cirrus.startup.TexturePreparation;
import remila.sex.cirrus.worldgen.DensityBufferReuse;
import remila.sex.cirrus.verification.ChunkPreparationBenchmark;
import remila.sex.cirrus.verification.TerrainCommandWorldStats;
import remila.sex.cirrus.verification.TerrainLodWorldStats;
import remila.sex.cirrus.verification.WorldOptimizationStats;
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
    @Unique private final long cirrus$start = System.nanoTime();
    @Unique private boolean cirrus$creating;
    @Unique private CirrusConfigScreen cirrus$configScreen;
    @Unique private long cirrus$configOpened;
    @Unique private long cirrus$joined;
    @Unique private long cirrus$phaseStart;
    @Unique private long cirrus$previousFrame;
    @Unique private int cirrus$phase;
    @Unique private int cirrus$initialHeight;
    @Unique private boolean cirrus$farScreenshot;
    @Unique private long cirrus$phaseDraws;
    @Unique private final List<Long> cirrus$frames = new ArrayList<>();
    @Unique private final StringBuilder cirrus$report = new StringBuilder();
    @Unique private final ChunkPreparationBenchmark cirrus$benchmark = new ChunkPreparationBenchmark();

    @Inject(method = "runTick", at = @At("TAIL"))
    private void cirrus$worldCheck(CallbackInfo ci) throws IOException {
        Minecraft client = (Minecraft) (Object) this;
        long now = System.nanoTime();
        if (now - cirrus$start > 420_000_000_000L) {
            throw new AssertionError("Timed out creating or testing world");
        }
        if (Boolean.getBoolean("cirrus.verifyStartup")) {
            if (!cirrus$creating && client.gui.overlay() == null && client.gui.screen() instanceof TitleScreen) {
                cirrus$creating = true;
                System.out.printf("cirrus_startup_ms=%d texture_preparation=%s%n",
                        ManagementFactory.getRuntimeMXBean().getUptime(), TexturePreparation.isEnabled());
                client.stop();
            }
            return;
        }
        if (!cirrus$creating) {
            if (client.gui.overlay() != null || (cirrus$configScreen == null && !(client.gui.screen() instanceof TitleScreen))) return;
            if (cirrus$configScreen == null) {
                cirrus$configScreen = FabricLoader.getInstance().isModLoaded("modmenu")
                        ? (CirrusConfigScreen) new CirrusModMenu().getModConfigScreenFactory().create(client.gui.screen())
                        : new CirrusConfigScreen(client.gui.screen());
                client.gui.setScreen(cirrus$configScreen);
                cirrus$configOpened = now;
                return;
            }
            if (now - cirrus$configOpened < 1_000_000_000L) return;
            long checkboxes = cirrus$configScreen.children().stream().filter(child -> child instanceof Checkbox).count();
            long expected = 2 + Arrays.stream(Optimization.values()).filter(option -> option.supports("26.3")).count();
            if (checkboxes != expected) throw new AssertionError("Expected " + expected + " checkboxes, got " + checkboxes);
            for (var child : cirrus$configScreen.children()) if (child instanceof AbstractWidget widget) {
                if (widget.getX() < 0 || widget.getY() < 0 || widget.getX() + widget.getWidth() > cirrus$configScreen.width
                        || widget.getY() + widget.getHeight() > cirrus$configScreen.height) throw new AssertionError("Config widget outside screen: " + widget.getMessage());
            }
            Screenshot.grab(client, false);
            cirrus$report.append("PASS: checkbox-only settings opened on real GPU; widgets fit window.\n");
            client.gui.setScreen(null);
            cirrus$creating = true;
            client.options.pauseOnLostFocus = false;
            client.options.enableVsync().set(false);
            client.options.framerateLimit().set(60);
            client.options.renderDistance().set(Integer.getInteger("cirrus.verifyDistance", 12));
            client.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
            TerrainLod.setEnabled(true);
            DensityBufferReuse.setEnabled(!WorldOptimizationStats.DENSITY_CONTROL);
            LevelSettings settings = new LevelSettings("Cirrus verification", GameType.CREATIVE,
                    new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false),
                    true, WorldDataConfiguration.DEFAULT);
            client.createWorldOpenFlows().createFreshLevel("Cirrus-test-" + System.currentTimeMillis(),
                    settings, new WorldOptions(1234, true, false),
                    WorldPresets::createNormalWorldDimensions, null);
            return;
        }
        if (client.player == null || client.getConnection() == null) return;
        if (WorldOptimizationStats.REPLAY_ONLY) {
            if (cirrus$benchmark.tick(client, now)) {
                Files.writeString(Path.of("cirrus-world-verification.txt"), cirrus$benchmark.report()
                        + "stable_command_upload_bytes=" + TerrainCommandWorldStats.reusedBytes() + "\n"
                        + "draw_group_replays=" + WorldOptimizationStats.drawGroupHits + "\n");
                client.stop();
            }
            return;
        }
        if (cirrus$phase == 6) {
            if (cirrus$benchmark.tick(client, now)) {
                cirrus$report.append(cirrus$benchmark.report())
                        .append("stable_command_upload_bytes=").append(TerrainCommandWorldStats.reusedBytes()).append("\n");
                if (TerrainCommandWorldStats.reusedBytes() == 0) throw new AssertionError("No stable command batches uploaded");
                cirrus$report.append(WorldOptimizationStats.report());
                Files.writeString(Path.of("cirrus-world-verification.txt"), cirrus$report);
                client.stop();
            }
            return;
        }
        if (cirrus$joined == 0) {
            cirrus$joined = now;
            client.gui.setScreen(null);
            cirrus$report.append("PASS: integrated server joined; automatic modules active.\n")
                    .append("Streaming teleports, seed 1234, configured render distance; automatic LOD thresholds, no Sodium; focus/GC make these exploratory timings, not FPS evidence.\n");
            return;
        }
        if (now - cirrus$joined < 20_000_000_000L) return;
        if (cirrus$phase == 0 && cirrus$phaseStart == 0) {
            String mode = WorldOptimizationStats.DENSITY_CONTROL ? "off" : "on";
            Files.writeString(Path.of("surface-states-" + mode + ".txt"), WorldOptimizationStats.surfaceStates());
            if (WorldOptimizationStats.DENSITY_CONTROL) {
                Files.writeString(Path.of("cirrus-world-verification.txt"), "Density control: generated 25 surface samples with native buffers.\n");
                client.stop();
                return;
            }
        }
        if (cirrus$phaseStart == 0) {
            cirrus$phaseStart = now;
            if (cirrus$phase == 4) {
                cirrus$initialHeight = client.getWindow().getHeight();
                client.getWindow().setWindowed(768, 400);
            }
            if (cirrus$phase == 5) {
                cirrus$report.append("framebuffer_resize_before=").append(cirrus$initialHeight)
                        .append(" after=").append(client.getWindow().getHeight()).append('\n');
                client.getWindow().setWindowed(854, 480);
                cirrus$phaseDraws = WorldOptimizationStats.farTerrainDraws;
            }
            cirrus$previousFrame = now;
            TerrainLod.setEnabled(cirrus$phase % 2 == 1);
            int phase = cirrus$phase;
            double height = cirrus$height(phase);
            var server = client.getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.connection.teleport(phase * 96.0, height, 0, phase == 5 ? 90 : 0, phase == 5 ? 17 : 30);
            });
            return;
        }
        if (now - cirrus$phaseStart > 1_000_000_000L) {
            cirrus$frames.add(now - cirrus$previousFrame);
        }
        cirrus$previousFrame = now;
        if (cirrus$phase == 5 && !cirrus$farScreenshot && now - cirrus$phaseStart >= 5_000_000_000L
                && WorldOptimizationStats.farTerrainDraws > cirrus$phaseDraws) {
            Screenshot.grab(client.gameDirectory, "distant-terrain-on.png", client.gameRenderer.mainRenderTarget(), 1, ignored -> {});
            cirrus$farScreenshot = true;
            FarTerrainLod.setEnabled(false);
        }
        if (now - cirrus$phaseStart < 6_000_000_000L) return;
        if (cirrus$phase == 5 && cirrus$farScreenshot) {
            Screenshot.grab(client.gameDirectory, "distant-terrain-off.png", client.gameRenderer.mainRenderTarget(), 1, ignored -> {});
            FarTerrainLod.setEnabled(true);
        }
        if (Math.abs(client.player.getX() - cirrus$phase * 96.0) > 8 || Math.abs(client.player.getY() - cirrus$height(cirrus$phase)) > 1) {
            throw new AssertionError("Streaming teleport was not acknowledged by the client");
        }
        long[] samples = cirrus$frames.stream().mapToLong(Long::longValue).sorted().toArray();
        double mean = java.util.Arrays.stream(samples).average().orElseThrow();
        cirrus$report.append("round=").append(cirrus$phase)
                .append(" enabled=").append(TerrainLod.isEnabled())
                .append(" teleport_acknowledged=true")
                .append(" focused=").append(client.isWindowActive())
                .append(" samples=").append(samples.length)
                .append(" average_fps=").append(1e9 / mean)
                .append(" p99_ms=").append(samples[(int) (samples.length * 0.99)] / 1e6)
                .append("\n");
        if (cirrus$phase == 3) Screenshot.grab(client, false);
        cirrus$phase++;
        cirrus$frames.clear();
        cirrus$phaseStart = 0;
        if (cirrus$phase == 6) {
            cirrus$report.append(TerrainLodWorldStats.report());
            if (TerrainLodWorldStats.compiled.get() == 0 || TerrainLodWorldStats.draws[1].get() == 0 || TerrainLodWorldStats.draws[2].get() == 0) {
                throw new AssertionError("Missing world LOD compilation/draws: " + TerrainLodWorldStats.report());
            }
        }
    }

    @Unique private static double cirrus$height(int phase) {
        return switch (phase % 3) { case 0 -> 96; case 1 -> 112; default -> 180; };
    }
}
