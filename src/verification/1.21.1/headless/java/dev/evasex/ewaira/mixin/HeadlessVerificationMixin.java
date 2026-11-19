package dev.evasex.ewaira.mixin;

import dev.evasex.ewaira.verification.NativeImageCopyVerification;
import dev.evasex.ewaira.verification.QuadEncodingVerification;
import dev.evasex.ewaira.verification.QuadEncodingBenchmark;
import dev.evasex.ewaira.verification.ModelGeometryVerification;
import dev.evasex.ewaira.verification.TerrainVerification;
import dev.evasex.ewaira.verification.ModelGeometryBenchmark;
import dev.evasex.ewaira.verification.OcclusionVerification;
import dev.evasex.ewaira.verification.LightingVerification;
import dev.evasex.ewaira.verification.FaceVisibilityVerification;
import dev.evasex.ewaira.verification.BlockEntityCullingVerification;
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
    private static void ewaira$verifyQuads(String[] args, CallbackInfo ci) throws IOException, ReflectiveOperationException, InterruptedException {
        String report = QuadEncodingVerification.verify(true) + QuadEncodingVerification.verifyTransforms(true)
                + LightingVerification.verify() + FaceVisibilityVerification.verify()
                + BlockEntityCullingVerification.verify() + OcclusionVerification.verify() + QuadEncodingBenchmark.run()
                + ModelGeometryVerification.verify() + ModelGeometryBenchmark.run() + TerrainVerification.verify()
                + NativeImageCopyVerification.verify();
        Files.writeString(Path.of("ewaira-quad-verification.txt"), report);
        System.out.println(report);
        ci.cancel();
    }
}
