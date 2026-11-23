package remila.sex.cirrus.mixin;

import remila.sex.cirrus.culling.EntityOcclusionEngine;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
abstract class EntityCullMixin {
    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private <E extends Entity> void cirrus$cullOccludedEntity(E entity, Frustum frustum,
                                                              double camX, double camY, double camZ, float partialTick,
                                                              CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && EntityOcclusionEngine.isEntityOccluded(entity, camX, camY, camZ)) {
            cir.setReturnValue(false);
        }
    }
}
