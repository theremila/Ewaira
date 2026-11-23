package remila.sex.cirrus.mixin;

import remila.sex.cirrus.text.TextOptimization;
import remila.sex.cirrus.verification.ParticleMeshVerification;
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
    @Unique private final long cirrus$start = System.nanoTime();
    @Unique private boolean cirrus$creating;
    @Unique private long cirrus$joined;
    @Unique private long cirrus$phaseStart;
    @Unique private long cirrus$previous;
    @Unique private int cirrus$phase;
    @Unique private Recording cirrus$recording;
    @Unique private final List<Long> cirrus$frames = new ArrayList<>();
    @Unique private final StringBuilder cirrus$report = new StringBuilder();

    @Inject(method = "runTick", at = @At("TAIL"))
    private void cirrus$profileWorld(CallbackInfo ci) throws IOException {
        Minecraft client = (Minecraft) (Object) this;
        long now = System.nanoTime();
        if (now - cirrus$start > 240_000_000_000L) throw new AssertionError("World profile timed out");
        if (!cirrus$creating) {
            if (now - cirrus$start < 25_000_000_000L) return;
            cirrus$creating = true;
            client.options.pauseOnLostFocus = false;
            client.options.enableVsync().set(false);
            client.options.framerateLimit().set(Boolean.getBoolean("cirrus.meshOnly") ? 30 : 260);
            client.options.renderDistance().set(Boolean.getBoolean("cirrus.meshOnly") ? 2 : 8);
            client.options.simulationDistance().set(5);
            TextOptimization.setEnabled(false);
            LevelSettings settings = new LevelSettings("Cirrus profile", GameType.CREATIVE,
                    false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
            client.createWorldOpenFlows().createFreshLevel("Cirrus-profile-" + System.currentTimeMillis(),
                    settings, new WorldOptions(1234, true, false),
                    WorldPresets::createNormalWorldDimensions, null);
            return;
        }
        if (client.player == null || client.level == null) return;
        client.setWindowActive(true);
        client.options.pauseOnLostFocus = false;
        client.setScreen(null);
        if (cirrus$joined == 0) {
            cirrus$joined = now;
            client.setScreen(null);
            return;
        }
        if (now - cirrus$joined < 20_000_000_000L) return;
        if (Boolean.getBoolean("cirrus.meshOnly")) {
            String report = ParticleMeshVerification.verify(client);
            Files.writeString(Path.of("cirrus-particle-mesh.txt"), report);
            client.stop();
            return;
        }
        if (cirrus$phaseStart == 0) {
            cirrus$phaseStart = now;
            cirrus$previous = now;
            cirrus$recording = new Recording();
            cirrus$recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(5));
            cirrus$recording.enable("jdk.ObjectAllocationSample");
            cirrus$recording.start();
            if (cirrus$phase == 1) {
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
        if (now - cirrus$phaseStart > 2_000_000_000L) cirrus$frames.add(now - cirrus$previous);
        cirrus$previous = now;
        if (now - cirrus$phaseStart < 16_000_000_000L) return;
        String scene = cirrus$phase == 0 ? "normal" : "particles-behind";
        cirrus$recording.stop();
        cirrus$recording.dump(Path.of("cirrus-" + scene + ".jfr"));
        cirrus$recording.close();
        long[] samples = cirrus$frames.stream().mapToLong(Long::longValue).sorted().toArray();
        cirrus$report.append(scene).append(" samples=").append(samples.length)
                .append(" fps=").append(1e9 / Arrays.stream(samples).average().orElseThrow())
                .append(" p99_ms=").append(samples[(int) (samples.length * 0.99)] / 1e6)
                .append(" particles=").append(client.particleEngine.countParticles()).append("\n");
        cirrus$phase++;
        cirrus$frames.clear();
        cirrus$phaseStart = 0;
        if (cirrus$phase == 2) {
            Files.writeString(Path.of("cirrus-profile.txt"), cirrus$report);
            client.stop();
        }
    }
}
