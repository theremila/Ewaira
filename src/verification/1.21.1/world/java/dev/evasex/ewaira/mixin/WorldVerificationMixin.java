package dev.evasex.ewaira.mixin;

import dev.evasex.ewaira.text.TextOptimization;
import dev.evasex.ewaira.verification.ParticleMeshVerification;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jdk.jfr.Recording;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
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

/** Disposable profiling world, excluded from production builds. */
@Mixin(Minecraft.class)
abstract class WorldVerificationMixin {
    @Unique private final long ewaira$start = System.nanoTime();
    @Unique private boolean ewaira$creating;
    @Unique private long ewaira$joined;
    @Unique private long ewaira$phaseStart;
    @Unique private long ewaira$previous;
    @Unique private int ewaira$phase;
    @Unique private Recording ewaira$recording;
    @Unique private final List<Long> ewaira$frames = new ArrayList<>();
    @Unique private final StringBuilder ewaira$report = new StringBuilder();

    @Inject(method = "runTick", at = @At("TAIL"))
    private void ewaira$profileWorld(CallbackInfo ci) throws IOException {
        Minecraft client = (Minecraft) (Object) this;
        long now = System.nanoTime();
        if (now - ewaira$start > 240_000_000_000L) throw new AssertionError("World profile timed out");
        if (!ewaira$creating) {
            if (now - ewaira$start < 25_000_000_000L) return;
            ewaira$creating = true;
            client.options.pauseOnLostFocus = false;
            client.options.enableVsync().set(false);
            client.options.framerateLimit().set(Boolean.getBoolean("ewaira.meshOnly") ? 30 : 260);
            client.options.renderDistance().set(Boolean.getBoolean("ewaira.meshOnly") ? 2 : 8);
            client.options.simulationDistance().set(5);
            TextOptimization.setEnabled(false);
            LevelSettings settings = new LevelSettings("Ewaira profile", GameType.CREATIVE,
                    false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
            client.createWorldOpenFlows().createFreshLevel("Ewaira-profile-" + System.currentTimeMillis(),
                    settings, new WorldOptions(1234, true, false),
                    WorldPresets::createNormalWorldDimensions, null);
            return;
        }
        if (client.player == null || client.level == null) return;
        client.setWindowActive(true);
        client.options.pauseOnLostFocus = false;
        client.setScreen(null);
        if (ewaira$joined == 0) {
            ewaira$joined = now;
            client.setScreen(null);
            return;
        }
        if (now - ewaira$joined < 20_000_000_000L) return;
        if (Boolean.getBoolean("ewaira.meshOnly")) {
            String report = ParticleMeshVerification.verify(client);
            Files.writeString(Path.of("ewaira-particle-mesh.txt"), report);
            client.stop();
            return;
        }
        if (ewaira$phaseStart == 0) {
            ewaira$phaseStart = now;
            ewaira$previous = now;
            ewaira$recording = new Recording();
            ewaira$recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(5));
            ewaira$recording.enable("jdk.ObjectAllocationSample");
            ewaira$recording.start();
            if (ewaira$phase == 1) {
                for (int i = 0; i < 4096; i++) {
                    Particle particle = client.particleEngine.createParticle(ParticleTypes.FLAME,
                            client.player.getX() + (i % 32 - 16) * 0.1,
                            client.player.getEyeY() + (i / 32 % 16 - 8) * 0.1,
                            client.player.getZ() - 8 - (i / 512) * 0.1, 0, 0, 0);
                    if (particle == null) throw new AssertionError("No flame particle");
                    particle.setLifetime(10000);
                    particle.setParticleSpeed(0, 0, 0);
                }
                client.player.setYRot(0);
                client.player.setXRot(0);
            }
            return;
        }
        if (now - ewaira$phaseStart > 2_000_000_000L) ewaira$frames.add(now - ewaira$previous);
        ewaira$previous = now;
        if (now - ewaira$phaseStart < 16_000_000_000L) return;
        String scene = ewaira$phase == 0 ? "normal" : "particles-behind";
        ewaira$recording.stop();
        ewaira$recording.dump(Path.of("ewaira-" + scene + ".jfr"));
        ewaira$recording.close();
        long[] samples = ewaira$frames.stream().mapToLong(Long::longValue).sorted().toArray();
        ewaira$report.append(scene).append(" samples=").append(samples.length)
                .append(" fps=").append(1e9 / Arrays.stream(samples).average().orElseThrow())
                .append(" p99_ms=").append(samples[(int) (samples.length * 0.99)] / 1e6)
                .append(" particles=").append(client.particleEngine.countParticles()).append("\n");
        ewaira$phase++;
        ewaira$frames.clear();
        ewaira$phaseStart = 0;
        if (ewaira$phase == 2) {
            Files.writeString(Path.of("ewaira-profile.txt"), ewaira$report);
            client.stop();
        }
    }
}
