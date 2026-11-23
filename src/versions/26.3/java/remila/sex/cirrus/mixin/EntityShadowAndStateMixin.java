package remila.sex.cirrus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import remila.sex.cirrus.culling.EntityOcclusionEngine;
import remila.sex.cirrus.culling.EntityRenderOptimization;
import java.util.ArrayList;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cuts render-thread GC pressure and CPU cost in dense entity crowds (e.g. mob farms):
 * 1. Skips redundant shadow block queries beyond 24 blocks or > 8 entities per 2x2 cell.
 * 2. Reuses frame-scoped {@link EntityRenderState} objects for verified vanilla mob/item state classes.
 */
@Mixin(EntityRenderer.class)
abstract class EntityShadowAndStateMixin<T extends Entity, S extends EntityRenderState> {
    @Unique private static final int MAX_POOLED_STATES_PER_RENDERER = 1024;
    @Unique private static final Set<String> CIRRUS$POOLABLE_STATES = Set.of(
            "net.minecraft.client.renderer.entity.state.PigRenderState",
            "net.minecraft.client.renderer.entity.state.CowRenderState",
            "net.minecraft.client.renderer.entity.state.SheepRenderState",
            "net.minecraft.client.renderer.entity.state.ChickenRenderState",
            "net.minecraft.client.renderer.entity.state.ZombieRenderState",
            "net.minecraft.client.renderer.entity.state.SkeletonRenderState",
            "net.minecraft.client.renderer.entity.state.CreeperRenderState",
            "net.minecraft.client.renderer.entity.state.ItemEntityRenderState",
            "net.minecraft.client.renderer.entity.state.ExperienceOrbRenderState",
            "net.minecraft.client.renderer.entity.state.LivingEntityRenderState"
    );

    @Unique private ArrayList<S> cirrus$statePool;
    @Unique private int cirrus$poolEpoch = -1;
    @Unique private int cirrus$poolCursor;
    @Unique private byte cirrus$poolEligibility; // 0 = unknown, 1 = eligible, -1 = ineligible

    @Inject(method = "extractShadow", at = @At("HEAD"), cancellable = true)
    private void cirrus$cullCrowdShadows(S state, Minecraft client, Level level, CallbackInfo ci) {
        if (!EntityRenderOptimization.shouldExtractShadow(state.x, state.z, state.distanceToCameraSq)) {
            state.shadowPieces.clear();
            state.shadowRadius = 0.0f;
            ci.cancel();
        }
    }

    @WrapOperation(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;createRenderState()Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"))
    private S cirrus$poolRenderState(EntityRenderer<T, S> self, Operation<S> vanilla) {
        if (!EntityOcclusionEngine.isEnabled() || cirrus$poolEligibility < 0) {
            return vanilla.call(self);
        }
        int epoch = EntityRenderOptimization.frameEpoch();
        if (cirrus$poolEpoch != epoch) {
            cirrus$poolEpoch = epoch;
            cirrus$poolCursor = 0;
        }
        if (cirrus$poolEligibility == 1 && cirrus$statePool != null && cirrus$poolCursor < cirrus$statePool.size()) {
            S reused = cirrus$statePool.get(cirrus$poolCursor++);
            resetBaseState(reused);
            return reused;
        }
        S fresh = vanilla.call(self);
        if (fresh == null) return null;
        if (cirrus$poolEligibility == 0) {
            cirrus$poolEligibility = (byte) (CIRRUS$POOLABLE_STATES.contains(fresh.getClass().getName()) ? 1 : -1);
            if (cirrus$poolEligibility < 0) return fresh;
            cirrus$statePool = new ArrayList<>(32);
        }
        if (cirrus$statePool.size() < MAX_POOLED_STATES_PER_RENDERER) {
            cirrus$statePool.add(fresh);
            cirrus$poolCursor = cirrus$statePool.size();
        }
        return fresh;
    }

    @Unique
    private static void resetBaseState(EntityRenderState state) {
        state.shadowPieces.clear();
        state.shadowRadius = 0.0f;
        state.nameTag = null;
        state.scoreText = null;
        state.nameTagAttachment = null;
        state.leashStates = null;
        state.passengerOffset = null;
        state.outlineColor = 0;
        state.displayFireAnimation = false;
        state.isInvisible = false;
        state.isDiscrete = false;
        if (state instanceof LivingEntityRenderState living) {
            living.headItem.clear();
            living.bedOrientation = null;
            living.wornHeadType = null;
            living.wornHeadProfile = null;
            living.isUpsideDown = false;
            living.isFullyFrozen = false;
            living.isBaby = false;
            living.isInWater = false;
            living.isAutoSpinAttack = false;
            living.hasRedOverlay = false;
            living.isInvisibleToPlayer = false;
        }
    }
}
