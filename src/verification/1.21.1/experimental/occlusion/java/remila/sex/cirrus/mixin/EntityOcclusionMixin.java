package remila.sex.cirrus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import remila.sex.cirrus.occlusion.EntityOcclusion;
import remila.sex.cirrus.occlusion.OcclusionProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.entity.CowRenderer;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.PigRenderer;
import net.minecraft.client.renderer.entity.SheepRenderer;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class EntityOcclusionMixin {
    @Unique private final OcclusionProbe cirrus$probe = new OcclusionProbe();
    @Inject(method = "renderEntity", at = @At("HEAD"), cancellable = true)
    private void cirrus$skipHiddenMob(Entity entity, double cameraX, double cameraY, double cameraZ,
            float tick, PoseStack poses, MultiBufferSource buffers, CallbackInfo ci) {
        if (!EntityOcclusion.isEnabled() || !(entity instanceof Mob mob)) return;
        EntityType<?> entityType = entity.getType();
        if (entityType != EntityType.ZOMBIE && entityType != EntityType.SKELETON && entityType != EntityType.CREEPER
                && entityType != EntityType.COW && entityType != EntityType.PIG && entityType != EntityType.SHEEP) return;
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread() || client.level == null || entity.level() != client.level
                || client.getEntityRenderDispatcher().shouldRenderHitBoxes()
                || entity.hasCustomName() || entity.shouldShowName() || entity.isOnFire() || entity.isInvisible()
                || entity.isPassenger() || entity.isVehicle() || mob.getLeashData() != null
                || client.shouldEntityAppearGlowing(entity)) return;
        EntityRenderer<?> renderer = client.getEntityRenderDispatcher().getRenderer(entity);
        Class<?> type = renderer.getClass();
        if (type != ZombieRenderer.class && type != SkeletonRenderer.class && type != CreeperRenderer.class
                && type != CowRenderer.class && type != PigRenderer.class && type != SheepRenderer.class) return;
        LevelRendererAccessor levelRenderer = (LevelRendererAccessor) this;
        ViewArea area = levelRenderer.cirrus$getViewArea();
        if (area == null || levelRenderer.cirrus$getCapturedFrustum() != null) return;
        Vec3 camera = client.gameRenderer.getMainCamera().getPosition();
        if (camera.x != cameraX || camera.y != cameraY || camera.z != cameraZ) return;
        AABB box = entity.getBoundingBoxForCulling();
        double x = Mth.lerp(tick, entity.xOld, entity.getX()) - entity.getX();
        double y = Mth.lerp(tick, entity.yOld, entity.getY()) - entity.getY();
        double z = Mth.lerp(tick, entity.zOld, entity.getZ()) - entity.getZ();
        // shortcut: envelope covers these six vanilla renderers; expand only after model/visual verification.
        AABB envelope = new AABB(box.minX + x - 2, box.minY + y - 2, box.minZ + z - 2,
                box.maxX + x + 2, box.maxY + y + 2, box.maxZ + z + 2);
        boolean hidden = cirrus$probe.hidden(area, camera, envelope);
        EntityOcclusion.record(hidden);
        if (hidden) ci.cancel();
    }
}
