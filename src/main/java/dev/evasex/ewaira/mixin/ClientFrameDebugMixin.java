package dev.evasex.ewaira.mixin;

import dev.evasex.ewaira.debug.RenderDiagnostics;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class ClientFrameDebugMixin {
    @Inject(method = "runTick", at = @At("TAIL"))
    private void ewaira$recordFrame(CallbackInfo ci) {
        if (RenderDiagnostics.enabled()) RenderDiagnostics.frame();
    }
}
