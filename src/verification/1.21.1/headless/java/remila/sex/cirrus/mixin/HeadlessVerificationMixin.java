package remila.sex.cirrus.mixin;

import remila.sex.cirrus.verification.NativeImageCopyVerification;
import remila.sex.cirrus.verification.QuadEncodingVerification;
import remila.sex.cirrus.verification.QuadEncodingBenchmark;
import remila.sex.cirrus.verification.ModelGeometryVerification;
import remila.sex.cirrus.verification.TerrainVerification;
import remila.sex.cirrus.verification.ModelGeometryBenchmark;
import remila.sex.cirrus.verification.OcclusionVerification;
import remila.sex.cirrus.verification.LightingVerification;
import remila.sex.cirrus.verification.FaceVisibilityVerification;
import remila.sex.cirrus.verification.BlockEntityCullingVerification;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs before Minecraft initializes a window or graphics device; development only. */
@Mixin(Main.class)
abstract class HeadlessVerificationMixin {
    @Inject(method = "main", at = @At("HEAD"), cancellable = true)
    private static void cirrus$verifyQuads(String[] args, CallbackInfo ci) throws IOException, ReflectiveOperationException, InterruptedException {
        String report = QuadEncodingVerification.verify(true) + QuadEncodingVerification.verifyTransforms(true)
                + LightingVerification.verify() + FaceVisibilityVerification.verify()
                + BlockEntityCullingVerification.verify() + OcclusionVerification.verify() + QuadEncodingBenchmark.run()
                + ModelGeometryVerification.verify() + ModelGeometryBenchmark.run() + TerrainVerification.verify()
                + NativeImageCopyVerification.verify();
        Files.writeString(Path.of("cirrus-quad-verification.txt"), report);
        System.out.println(report);
        ci.cancel();
    }
}
