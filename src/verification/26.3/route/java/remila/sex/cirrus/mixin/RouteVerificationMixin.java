package remila.sex.cirrus.mixin;

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
    @Unique private final long cirrus$deadline = System.nanoTime() + 250_000_000_000L;
    @Unique private final long[] cirrus$times = new long[65_536];
    @Unique private int cirrus$frames, cirrus$point;
    @Unique private long cirrus$joined, cirrus$previous, cirrus$nextPoint, cirrus$measureStart;
    @Unique private long cirrus$allocation, cirrus$gcCount, cirrus$gcTime, cirrus$stopAt;
    @Unique private boolean cirrus$creating, cirrus$done;
    @Unique private String cirrus$version;

    @Inject(method = "runTick", at = @At("TAIL"))
    private void cirrus$testRoute(CallbackInfo ci) throws IOException {
        Minecraft client = (Minecraft) (Object) this;
        long now = System.nanoTime();
        if (cirrus$done) {
            if (now >= cirrus$stopAt) cirrus$end(client);
            return;
        }
        if (now > cirrus$deadline) throw new AssertionError("Timed out during benchmark route");
        if (!cirrus$creating) {
            if (client.gui.overlay() != null || !(client.gui.screen() instanceof TitleScreen)) return;
            cirrus$createLevel(client);
            return;
        }
        if (client.player == null || client.getSingleplayerServer() == null) return;
        if (cirrus$joined == 0) {
            cirrus$joined = now;
            client.gui.setScreen(null);
            cirrus$teleport(client, 0);
            return;
        }
        if (now - cirrus$joined < 24_000_000_000L) return;
        if (cirrus$measureStart == 0) {
            cirrus$allocation = cirrus$allocated();
            cirrus$gcCount = cirrus$gc(false);
            cirrus$gcTime = cirrus$gc(true);
            cirrus$previous = cirrus$measureStart = cirrus$nextPoint = now;
        } else {
            cirrus$times[cirrus$frames++] = now - cirrus$previous;
            cirrus$previous = now;
        }
        if (now < cirrus$nextPoint || Math.abs(client.player.getX() - cirrus$x(cirrus$point)) > 1) return;
        if (cirrus$point == 180) {
            cirrus$finish(client, now);
            return;
        }
        cirrus$point++;
        cirrus$nextPoint += 125_000_000L;
        cirrus$teleport(client, cirrus$point);
    }

    @Unique private void cirrus$createLevel(Minecraft client) {
        var loader = FabricLoader.getInstance();
        cirrus$version = loader.getModContainer("cirrus").map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("vanilla");
        String expected = System.getProperty("cirrus.routeVariant").split("-", 2)[0];
        if (!cirrus$version.equals(expected) && !cirrus$version.startsWith(expected + "+"))
            throw new AssertionError("Expected " + expected + ", loaded " + cirrus$version);
        if (loader.isModLoaded("sodium")) throw new AssertionError("Sodium present");
        cirrus$creating = true;
        client.options.pauseOnLostFocus = false;
        client.options.enableVsync().set(false);
        client.options.framerateLimit().set(260);
        client.options.renderDistance().set(Integer.getInteger("cirrus.routeDistance"));
        client.options.simulationDistance().set(8);
        client.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
        String savedWorld = System.getProperty("cirrus.routeWorld");
        if (savedWorld != null) {
            client.createWorldOpenFlows().openWorld(savedWorld, () -> {
                throw new AssertionError("Did not open benchmark world " + savedWorld);
            });
            return;
        }
        LevelSettings settings = new LevelSettings("Cirrus route", GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        client.createWorldOpenFlows().createFreshLevel("route-" + System.currentTimeMillis(), settings,
                new WorldOptions(1234, true, false), WorldPresets::createNormalWorldDimensions, null);
    }

    @Unique private static double cirrus$x(int index) { return -528 + index * 4.0; }

    @Unique private void cirrus$teleport(Minecraft client, int step) {
        var server = client.getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            if (!player.getAbilities().flying) {
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
            }
            player.connection.teleport(cirrus$x(step), 143, 64, step < 170 ? -88 : 90, 14);
        });
    }

    @Unique private void cirrus$finish(Minecraft client, long now) throws IOException {
        cirrus$done = true;
        long allocated = cirrus$allocated();
        Arrays.sort(cirrus$times, 0, cirrus$frames);
        long elapsed = now - cirrus$measureStart;
        String report = String.format(Locale.ROOT,
                "version=%s distance=%d framebuffer=%dx%d focused=%s steps=%d frames=%d elapsed_s=%.3f fps=%.3f p50_ms=%.3f p95_ms=%.3f p99_ms=%.3f render_alloc_kib_frame=%.3f gc_count=%d gc_ms=%d%n",
                cirrus$version, client.options.getEffectiveRenderDistance(), client.getWindow().getWidth(), client.getWindow().getHeight(),
                client.isWindowActive(), cirrus$point, cirrus$frames, elapsed / 1e9, cirrus$frames * 1e9 / elapsed,
                cirrus$percentile(50), cirrus$percentile(95), cirrus$percentile(99),
                allocated < 0 ? -1 : (allocated - cirrus$allocation) / (1024.0 * cirrus$frames),
                cirrus$gc(false) - cirrus$gcCount, cirrus$gc(true) - cirrus$gcTime);
        Files.writeString(Path.of("route-result.txt"), report);
        if (System.getProperty("cirrus.routeVariant").endsWith("-debug")) cirrus$stopAt = now + 8_000_000_000L;
        else cirrus$end(client);
    }

    @Unique private void cirrus$end(Minecraft client) {
        cirrus$stopAt = Long.MAX_VALUE;
        Screenshot.grab(client.gameDirectory, "route-end.png", client.gameRenderer.mainRenderTarget(), 1, ignored -> {});
        client.stop();
    }

    @Unique private double cirrus$percentile(int percent) {
        return cirrus$times[(cirrus$frames * percent - 1) / 100] / 1e6;
    }

    @Unique private static long cirrus$gc(boolean time) {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(time ? GarbageCollectorMXBean::getCollectionTime : GarbageCollectorMXBean::getCollectionCount)
                .filter(value -> value >= 0).sum();
    }

    @Unique private static long cirrus$allocated() {
        return ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean && bean.isThreadAllocatedMemoryEnabled()
                ? bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) : -1;
    }
}
