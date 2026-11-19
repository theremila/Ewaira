package dev.evasex.ewaira.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.evasex.ewaira.text.FontWidthCacheAccess;
import dev.evasex.ewaira.text.TextOptimization;
import dev.evasex.ewaira.text.TextWidthCache;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Font.class)
abstract class FontMixin implements FontWidthCacheAccess {
    @Unique private final TextWidthCache ewaira$widthCache = new TextWidthCache();

    @Override
    public TextWidthCache ewaira$getWidthCache() {
        return ewaira$widthCache;
    }

    @WrapOperation(method = "width(Ljava/lang/String;)I", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/StringSplitter;stringWidth(Ljava/lang/String;)F"))
    private float ewaira$cachedWidth(StringSplitter splitter, String text, Operation<Float> original) {
        if (!TextOptimization.isEnabled() || !ewaira$widthCache.accepts(text)) {
            return original.call(splitter, text);
        }
        float cached = ewaira$widthCache.get(text, TextOptimization.fontGeneration());
        if (!Float.isNaN(cached)) {
            return cached;
        }
        float width = original.call(splitter, text);
        ewaira$widthCache.put(text, width);
        return width;
    }
}
