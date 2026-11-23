package remila.sex.cirrus.mixin;

import remila.sex.cirrus.culling.EntityRenderOptimization;
import remila.sex.cirrus.debug.RenderDiagnostics;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class ClientFrameDebugMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void cirrus$beginFrame(CallbackInfo ci) {
        EntityRenderOptimization.beginFrame();
    }

    @Inject(method = "runTick", at = @At("TAIL"))
    private void cirrus$recordFrame(CallbackInfo ci) {
        if (RenderDiagnostics.enabled()) RenderDiagnostics.frame();
    }
}
