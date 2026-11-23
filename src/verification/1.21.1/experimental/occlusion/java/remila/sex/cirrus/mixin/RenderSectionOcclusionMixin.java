package remila.sex.cirrus.mixin;

import remila.sex.cirrus.occlusion.ReadyOccluders;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionRenderDispatcher.RenderSection.class)
abstract class RenderSectionOcclusionMixin implements ReadyOccluders {
    @Unique private volatile boolean cirrus$ready;
    public boolean cirrus$occlusionReady() { return cirrus$ready; }
    @Inject(method = {"reset", "setDirty"}, at = @At("HEAD"))
    private void cirrus$invalidate(CallbackInfo ci) { cirrus$ready = false; }
    @Inject(method = "createCompileTask", at = @At("HEAD"))
    private void cirrus$pendingUpload(CallbackInfoReturnable<?> ci) { cirrus$ready = false; }
    @Inject(method = "setCompiled", at = @At("RETURN"))
    private void cirrus$published(CallbackInfo ci) { cirrus$ready = true; }
}
