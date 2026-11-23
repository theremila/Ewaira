package remila.sex.cirrus.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import remila.sex.cirrus.mesh.FaceVisibility;
import remila.sex.cirrus.mesh.FaceVisibilityCache;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Block.class)
abstract class BlockFaceMixin {
    @Unique private static final ThreadLocal<FaceVisibilityCache> cirrus$faces = ThreadLocal.withInitial(FaceVisibilityCache::new);

    @WrapMethod(method = "shouldRenderFace")
    private static boolean cirrus$faceVisibility(BlockState state, BlockState neighbor,
            Direction direction, Operation<Boolean> original) {
        if (!FaceVisibility.isEnabled()) return original.call(state, neighbor, direction);
        // Keep vanilla's shortcut order, including full neighbor faces before skipRendering.
        VoxelShape other = neighbor.getFaceOcclusionShape(direction.getOpposite());
        if (other == Shapes.block()) return false;
        if (state.skipRendering(neighbor, direction)) return false;
        if (other == Shapes.empty()) return true;
        VoxelShape shape = state.getFaceOcclusionShape(direction);
        if (shape == Shapes.empty()) return true;
        FaceVisibilityCache cache = cirrus$faces.get();
        int cached = cache.get(shape, other, 0);
        if (cached != FaceVisibilityCache.MISS) return cached == 1;
        boolean visible = Shapes.joinIsNotEmpty(shape, other, BooleanOp.ONLY_FIRST);
        cache.put(shape, other, 0, visible);
        return visible;
    }
}
