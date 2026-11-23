package remila.sex.cirrus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import remila.sex.cirrus.text.FontWidthCacheAccess;
import remila.sex.cirrus.text.TextOptimization;
import remila.sex.cirrus.text.TextWidthCache;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Font.class)
abstract class FontMixin implements FontWidthCacheAccess {
    @Unique private final TextWidthCache cirrus$widthCache = new TextWidthCache();

    @Override
    public TextWidthCache cirrus$getWidthCache() {
        return cirrus$widthCache;
    }

    @WrapOperation(method = "width(Ljava/lang/String;)I", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/StringSplitter;stringWidth(Ljava/lang/String;)F"))
    private float cirrus$cachedWidth(StringSplitter splitter, String text, Operation<Float> original) {
        if (!TextOptimization.isEnabled() || !cirrus$widthCache.accepts(text)) {
            return original.call(splitter, text);
        }
        float cached = cirrus$widthCache.get(text, TextOptimization.fontGeneration());
        if (!Float.isNaN(cached)) {
            return cached;
        }
        float width = original.call(splitter, text);
        cirrus$widthCache.put(text, width);
        return width;
    }
}
