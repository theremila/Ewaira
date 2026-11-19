package dev.evasex.ewaira.mixin;

import dev.evasex.ewaira.occlusion.ReadyOccluders;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionRenderDispatcher.RenderSection.class)
abstract class RenderSectionOcclusionMixin implements ReadyOccluders {
    @Unique private volatile boolean ewaira$ready;
    public boolean ewaira$occlusionReady() { return ewaira$ready; }
    @Inject(method = {"reset", "setDirty"}, at = @At("HEAD"))
    private void ewaira$invalidate(CallbackInfo ci) { ewaira$ready = false; }
    @Inject(method = "createCompileTask", at = @At("HEAD"))
    private void ewaira$pendingUpload(CallbackInfoReturnable<?> ci) { ewaira$ready = false; }
    @Inject(method = "setCompiled", at = @At("RETURN"))
    private void ewaira$published(CallbackInfo ci) { ewaira$ready = true; }
}
