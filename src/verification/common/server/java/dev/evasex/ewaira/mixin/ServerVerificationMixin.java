package dev.evasex.ewaira.mixin;

import dev.evasex.ewaira.verification.BiomeSamplingVerification;
import dev.evasex.ewaira.EwairaServer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Loader and native common-mixin check, cancelled before server/world/EULA startup. */
@Mixin(Main.class)
abstract class ServerVerificationMixin {
    @Inject(method = "main", at = @At("HEAD"), cancellable = true)
    private static void ewaira$verifyServer(String[] args, CallbackInfo ci) throws Exception {
        new EwairaServer().onInitializeServer();
        String report = "PASS: common server initializer executed without client rendering classes.\n" + BiomeSamplingVerification.verify();
        String version = FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString();
        if (version.equals("26.3")) {
            report += Class.forName("dev.evasex.ewaira.verification.PalettePackingVerification").getMethod("verify").invoke(null);
            report += Class.forName("dev.evasex.ewaira.verification.ClimateScratchVerification").getMethod("verify").invoke(null);
            report += Class.forName("dev.evasex.ewaira.verification.SurfaceDensityVerification").getMethod("verify").invoke(null);
        }
        Files.writeString(Path.of("ewaira-server-verification.txt"), report);
        System.out.println(report);
        ci.cancel();
    }
}
