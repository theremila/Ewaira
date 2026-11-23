package remila.sex.cirrus.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import remila.sex.cirrus.startup.TextureColorBleed;
import remila.sex.cirrus.startup.TexturePreparation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureUtil.class)
abstract class TexturePreparationMixin {
    @Inject(method = "solidify", at = @At("HEAD"), cancellable = true)
    private static void cirrus$prepareTexture(NativeImage image, CallbackInfo ci) {
        if (TexturePreparation.isEnabled() && TextureColorBleed.fill(image)) ci.cancel();
    }
}
