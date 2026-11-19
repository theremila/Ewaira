package dev.evasex.ewaira.mixin;

import dev.evasex.ewaira.verification.ModernFaceVerification;
import dev.evasex.ewaira.verification.ModelGeometryVerification;
import dev.evasex.ewaira.verification.TerrainVerification;
import dev.evasex.ewaira.verification.ModelGeometryBenchmark;
import dev.evasex.ewaira.verification.NativeVertexVerification;
import dev.evasex.ewaira.verification.NativeImageCopyVerification;
import dev.evasex.ewaira.verification.RenderEncodingVerification;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.SharedConstants;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.main.Main;
import net.minecraft.server.Bootstrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Development-only entrypoint: cancels before window or graphics-device initialization. */
@Mixin(Main.class)
abstract class RenderVerificationMixin {
    @Inject(method = "main", at = @At("HEAD"), cancellable = true)
    private static void ewaira$verifyRendering(String[] args, CallbackInfo ci) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        String report = ModernFaceVerification.verify() + NativeVertexVerification.verifyTransforms()
                + RenderEncodingVerification.verify() + ModelGeometryVerification.verify() + ModelGeometryBenchmark.run() + TerrainVerification.verify();
        if (Boolean.getBoolean("ewaira.verifyLighting")) {
            report += Class.forName("dev.evasex.ewaira.verification.LightingVerification").getMethod("verify").invoke(null);
        }
        report += NativeImageCopyVerification.verify();
        String version = FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString();
        if (!version.equals("1.21.10")) {
            report += Class.forName("dev.evasex.ewaira.verification.TexturePreparationVerification").getMethod("verify").invoke(null);
        }
        if (version.equals("26.3")) {
            report += Class.forName("dev.evasex.ewaira.verification.ChunkPreparationVerification").getMethod("verify").invoke(null);
            report += Class.forName("dev.evasex.ewaira.verification.PalettePackingVerification").getMethod("verify").invoke(null);
            report += Class.forName("dev.evasex.ewaira.verification.ClimateScratchVerification").getMethod("verify").invoke(null);
            report += Class.forName("dev.evasex.ewaira.verification.SurfaceDensityVerification").getMethod("verify").invoke(null);
            report += Class.forName("dev.evasex.ewaira.terrain.TerrainCommandUploadVerification").getMethod("verify").invoke(null);
        }
        Files.writeString(Path.of("ewaira-render-verification.txt"), report);
        System.out.println(report);
        ci.cancel();
    }
}
