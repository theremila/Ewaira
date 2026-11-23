package remila.sex.cirrus.mixin;

import remila.sex.cirrus.text.TextOptimization;
import net.minecraft.client.gui.font.FontSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FontSet.class)
abstract class FontSetMixin {
    // Both reload overloads cover resource packs and font-option changes.
    @Inject(method = "reload", at = @At("HEAD"))
    private void cirrus$invalidateWidths(CallbackInfo ci) {
        TextOptimization.invalidateFonts();
    }
}
