package remila.sex.cirrus.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import remila.sex.cirrus.particle.ParticleCulling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SingleQuadParticle.class)
abstract class SingleQuadParticleMixin {
    @Shadow public abstract float getQuadSize(float partialTick);

    @Inject(method = "renderRotatedQuad(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Quaternionf;FFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void cirrus$skipOutsideView(VertexConsumer vertices, Quaternionf rotation,
            float x, float y, float z, float partialTick, CallbackInfo ci) {
        if (!ParticleCulling.isEnabled()) return;
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) return;
        LevelRendererAccessor renderer = (LevelRendererAccessor) client.levelRenderer;
        Frustum frustum = renderer.cirrus$getCullingFrustum();
        // A captured debug frustum can belong to an earlier camera position.
        if (frustum == null || renderer.cirrus$getCapturedFrustum() != null) return;
        float norm = rotation.lengthSquared();
        // Custom non-unit rotations are outside the billboard bound's contract.
        if (!Float.isFinite(norm) || Math.abs(norm - 1) > 0.0001f) return;
        double radius = ParticleCulling.radius(getQuadSize(partialTick));
        if (!Double.isFinite(radius) || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) return;
        Vec3 camera = client.gameRenderer.getMainCamera().getPosition();
        double centerX = camera.x + x;
        double centerY = camera.y + y;
        double centerZ = camera.z + z;
        boolean visible = ((FrustumInvoker) frustum).cirrus$containsBox(
                centerX - radius, centerY - radius, centerZ - radius,
                centerX + radius, centerY + radius, centerZ + radius);
        ParticleCulling.record(visible);
        if (!visible) ci.cancel();
    }
}
