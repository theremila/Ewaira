package dev.evasex.ewaira.mixin;

import com.sun.management.ThreadMXBean;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
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

/** Identical route driver for unmodified Fabric and either production jar. */
@Mixin(Minecraft.class)
abstract class RouteVerificationMixin {
    @Unique private final long ewaira$deadline = System.nanoTime() + 250_000_000_000L;
    @Unique private final long[] ewaira$times = new long[65_536];
    @Unique private int ewaira$frames, ewaira$point;
    @Unique private long ewaira$joined, ewaira$previous, ewaira$nextPoint, ewaira$measureStart;
    @Unique private long ewaira$allocation, ewaira$gcCount, ewaira$gcTime, ewaira$stopAt;
    @Unique private boolean ewaira$creating, ewaira$done;
    @Unique private String ewaira$version;

    @Inject(method = "runTick", at = @At("TAIL"))
    private void ewaira$testRoute(CallbackInfo ci) throws IOException {
        Minecraft client = (Minecraft) (Object) this;
        long now = System.nanoTime();
        if (ewaira$done) {
            if (now >= ewaira$stopAt) ewaira$end(client);
            return;
        }
        if (now > ewaira$deadline) throw new AssertionError("Timed out during benchmark route");
        if (!ewaira$creating) {
            if (client.gui.overlay() != null || !(client.gui.screen() instanceof TitleScreen)) return;
            ewaira$createLevel(client);
            return;
        }
        if (client.player == null || client.getSingleplayerServer() == null) return;
        if (ewaira$joined == 0) {
            ewaira$joined = now;
            client.gui.setScreen(null);
            ewaira$teleport(client, 0);
            return;
        }
        if (now - ewaira$joined < 24_000_000_000L) return;
        if (ewaira$measureStart == 0) {
            ewaira$allocation = ewaira$allocated();
            ewaira$gcCount = ewaira$gc(false);
            ewaira$gcTime = ewaira$gc(true);
            ewaira$previous = ewaira$measureStart = ewaira$nextPoint = now;
        } else {
            ewaira$times[ewaira$frames++] = now - ewaira$previous;
            ewaira$previous = now;
        }
        if (now < ewaira$nextPoint || Math.abs(client.player.getX() - ewaira$x(ewaira$point)) > 1) return;
        if (ewaira$point == 180) {
            ewaira$finish(client, now);
            return;
        }
        ewaira$point++;
        ewaira$nextPoint += 125_000_000L;
        ewaira$teleport(client, ewaira$point);
    }

    @Unique private void ewaira$createLevel(Minecraft client) {
        var loader = FabricLoader.getInstance();
        ewaira$version = loader.getModContainer("ewaira").map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("vanilla");
        String expected = System.getProperty("ewaira.routeVariant").split("-", 2)[0];
        if (!ewaira$version.equals(expected) && !ewaira$version.startsWith(expected + "+"))
            throw new AssertionError("Expected " + expected + ", loaded " + ewaira$version);
        if (loader.isModLoaded("sodium")) throw new AssertionError("Sodium present");
        ewaira$creating = true;
        client.options.pauseOnLostFocus = false;
        client.options.enableVsync().set(false);
        client.options.framerateLimit().set(260);
        client.options.renderDistance().set(Integer.getInteger("ewaira.routeDistance"));
        client.options.simulationDistance().set(8);
        client.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
        String savedWorld = System.getProperty("ewaira.routeWorld");
        if (savedWorld != null) {
            client.createWorldOpenFlows().openWorld(savedWorld, () -> {
                throw new AssertionError("Did not open benchmark world " + savedWorld);
            });
            return;
        }
        LevelSettings settings = new LevelSettings("Ewaira route", GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        client.createWorldOpenFlows().createFreshLevel("route-" + System.currentTimeMillis(), settings,
                new WorldOptions(1234, true, false), WorldPresets::createNormalWorldDimensions, null);
    }

    @Unique private static double ewaira$x(int index) { return -528 + index * 4.0; }

    @Unique private void ewaira$teleport(Minecraft client, int step) {
        var server = client.getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            if (!player.getAbilities().flying) {
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
            }
            player.connection.teleport(ewaira$x(step), 143, 64, step < 170 ? -88 : 90, 14);
        });
    }

    @Unique private void ewaira$finish(Minecraft client, long now) throws IOException {
        ewaira$done = true;
        long allocated = ewaira$allocated();
        Arrays.sort(ewaira$times, 0, ewaira$frames);
        long elapsed = now - ewaira$measureStart;
        String report = String.format(Locale.ROOT,
                "version=%s distance=%d framebuffer=%dx%d focused=%s steps=%d frames=%d elapsed_s=%.3f fps=%.3f p50_ms=%.3f p95_ms=%.3f p99_ms=%.3f render_alloc_kib_frame=%.3f gc_count=%d gc_ms=%d%n",
                ewaira$version, client.options.getEffectiveRenderDistance(), client.getWindow().getWidth(), client.getWindow().getHeight(),
                client.isWindowActive(), ewaira$point, ewaira$frames, elapsed / 1e9, ewaira$frames * 1e9 / elapsed,
                ewaira$percentile(50), ewaira$percentile(95), ewaira$percentile(99),
                allocated < 0 ? -1 : (allocated - ewaira$allocation) / (1024.0 * ewaira$frames),
                ewaira$gc(false) - ewaira$gcCount, ewaira$gc(true) - ewaira$gcTime);
        Files.writeString(Path.of("route-result.txt"), report);
        if (System.getProperty("ewaira.routeVariant").endsWith("-debug")) ewaira$stopAt = now + 8_000_000_000L;
        else ewaira$end(client);
    }

    @Unique private void ewaira$end(Minecraft client) {
        ewaira$stopAt = Long.MAX_VALUE;
        Screenshot.grab(client.gameDirectory, "route-end.png", client.gameRenderer.mainRenderTarget(), 1, ignored -> {});
        client.stop();
    }

    @Unique private double ewaira$percentile(int percent) {
        return ewaira$times[(ewaira$frames * percent - 1) / 100] / 1e6;
    }

    @Unique private static long ewaira$gc(boolean time) {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(time ? GarbageCollectorMXBean::getCollectionTime : GarbageCollectorMXBean::getCollectionCount)
                .filter(value -> value >= 0).sum();
    }

    @Unique private static long ewaira$allocated() {
        return ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean && bean.isThreadAllocatedMemoryEnabled()
                ? bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) : -1;
    }
}
