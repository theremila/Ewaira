package dev.evasex.ewaira.mixin;

import dev.evasex.ewaira.lod.AutomaticTuning;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class AutomaticTuningMixin {
    @Unique private long ewaira$nextTuning;
    @Unique private int ewaira$lastHeight, ewaira$lastDistance, ewaira$lastFov;
    @Inject(method = "runTick", at = @At("HEAD"))
    private void ewaira$tuneDistances(CallbackInfo ci) {
        long now = System.nanoTime();
        if (now < ewaira$nextTuning) return;
        ewaira$nextTuning = now + 1_000_000_000L;
        Minecraft client = (Minecraft) (Object) this;
        int height = client.getWindow().getHeight(), distance = client.options.renderDistance().get(), fov = client.options.fov().get();
        if (height == ewaira$lastHeight && distance == ewaira$lastDistance && fov == ewaira$lastFov) return;
        AutomaticTuning.apply(distance, height, fov);
        ewaira$lastHeight = height; ewaira$lastDistance = distance; ewaira$lastFov = fov;
    }
}
