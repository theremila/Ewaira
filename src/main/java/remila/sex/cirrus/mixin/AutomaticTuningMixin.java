package remila.sex.cirrus.mixin;

import remila.sex.cirrus.lod.AutomaticTuning;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class AutomaticTuningMixin {
    @Unique private long cirrus$nextTuning;
    @Unique private int cirrus$lastHeight, cirrus$lastDistance, cirrus$lastFov;
    @Inject(method = "runTick", at = @At("HEAD"))
    private void cirrus$tuneDistances(CallbackInfo ci) {
        long now = System.nanoTime();
        if (now < cirrus$nextTuning) return;
        cirrus$nextTuning = now + 1_000_000_000L;
        Minecraft client = (Minecraft) (Object) this;
        int height = client.getWindow().getHeight(), distance = client.options.renderDistance().get(), fov = client.options.fov().get();
        if (height == cirrus$lastHeight && distance == cirrus$lastDistance && fov == cirrus$lastFov) return;
        AutomaticTuning.apply(distance, height, fov);
        cirrus$lastHeight = height; cirrus$lastDistance = distance; cirrus$lastFov = fov;
    }
}
