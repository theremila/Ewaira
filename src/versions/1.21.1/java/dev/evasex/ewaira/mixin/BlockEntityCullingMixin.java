package dev.evasex.ewaira.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import dev.evasex.ewaira.culling.BlockEntityCulling;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.client.renderer.blockentity.HangingSignRenderer;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.BedRenderer;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.client.renderer.blockentity.BellRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restricts culling to bounded vanilla renderers; beams and arbitrary entity models are excluded. */
@Mixin(BlockEntityRenderDispatcher.class)
abstract class BlockEntityCullingMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity> void ewaira$skipOutsideView(E entity, float tick,
            PoseStack poses, MultiBufferSource buffers, CallbackInfo ci) {
        if (!BlockEntityCulling.isEnabled()) return;
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread() || client.level == null || entity.getLevel() != client.level) return;
        BlockEntityRenderDispatcher dispatcher = (BlockEntityRenderDispatcher) (Object) this;
        if (dispatcher.level != client.level || dispatcher.camera != client.gameRenderer.getMainCamera()) return;
        LevelRendererAccessor level = (LevelRendererAccessor) client.levelRenderer;
        Frustum frustum = level.ewaira$getCullingFrustum();
        if (frustum == null || level.ewaira$getCapturedFrustum() != null) return;
        BlockEntityRenderer<E> renderer = dispatcher.getRenderer(entity);
        if (renderer == null || renderer.shouldRenderOffScreen(entity)) return;
        Class<?> type = renderer.getClass();
        if (type != SignRenderer.class && type != HangingSignRenderer.class
                && type != ChestRenderer.class
                && type != BedRenderer.class && type != SkullBlockRenderer.class
                && type != ShulkerBoxRenderer.class && type != EnchantTableRenderer.class
                && type != BellRenderer.class) return;
        // Five-block envelope includes animated lids, sign text, the other bed/chest half and books.
        BlockPos pos = entity.getBlockPos();
        if (!((FrustumInvoker) frustum).ewaira$containsBox(pos.getX() - 2.0, pos.getY() - 2.0,
                pos.getZ() - 2.0, pos.getX() + 3.0, pos.getY() + 3.0, pos.getZ() + 3.0)) ci.cancel();
    }
}
