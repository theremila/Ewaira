package remila.sex.cirrus.mixin;

import remila.sex.cirrus.verification.BiomeSamplingVerification;
import remila.sex.cirrus.CirrusServer;
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
    private static void cirrus$verifyServer(String[] args, CallbackInfo ci) throws Exception {
        new CirrusServer().onInitializeServer();
        String report = "PASS: common server initializer executed without client rendering classes.\n" + BiomeSamplingVerification.verify();
        String version = FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString();
        if (version.equals("26.3")) {
            report += Class.forName("remila.sex.cirrus.verification.PalettePackingVerification").getMethod("verify").invoke(null);
            report += Class.forName("remila.sex.cirrus.verification.ClimateScratchVerification").getMethod("verify").invoke(null);
            report += Class.forName("remila.sex.cirrus.verification.SurfaceDensityVerification").getMethod("verify").invoke(null);
        }
        Files.writeString(Path.of("cirrus-server-verification.txt"), report);
        System.out.println(report);
        ci.cancel();
    }
}
