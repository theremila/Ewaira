package dev.evasex.ewaira.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import dev.evasex.ewaira.startup.TextureCopies;
import dev.evasex.ewaira.startup.TexturePreparation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NativeImage.class)
abstract class NativeImageCopyMixin {
    @Inject(method = "copyRect(Lcom/mojang/blaze3d/platform/NativeImage;IIIIIIZZ)V", at = @At("HEAD"), cancellable = true)
    private void ewaira$copyRows(NativeImage target, int sx, int sy, int tx, int ty, int width, int height,
            boolean flipX, boolean flipY, CallbackInfo ci) {
        if (TexturePreparation.isEnabled() && TextureCopies.copy((NativeImage) (Object) this, target,
                sx, sy, tx, ty, width, height, flipX, flipY)) ci.cancel();
    }
}
