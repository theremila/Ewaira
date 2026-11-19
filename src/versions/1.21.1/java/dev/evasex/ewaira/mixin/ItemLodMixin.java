package dev.evasex.ewaira.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.evasex.ewaira.lod.ItemLod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemEntityRenderer.class)
abstract class ItemLodMixin {
    @WrapMethod(method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void ewaira$itemLod(ItemEntity entity, float yaw, float partialTick, PoseStack pose,
                                    MultiBufferSource buffers, int light, Operation<Void> original) {
        if (!ItemLod.enabled() || entity.getItem().getCount() <= 1) { original.call(entity, yaw, partialTick, pose, buffers, light); return; }
        double previous = ItemLod.enter(Minecraft.getInstance().getEntityRenderDispatcher().distanceToSqr(entity));
        try { original.call(entity, yaw, partialTick, pose, buffers, light); }
        finally { ItemLod.restore(previous); }
    }
    @ModifyReturnValue(method = "getRenderedAmount", at = @At("RETURN"))
    private static int ewaira$itemCount(int original) { return ItemLod.scopedCount(original); }
}
