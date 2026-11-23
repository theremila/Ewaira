package remila.sex.cirrus.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import remila.sex.cirrus.mesh.FaceVisibility;
import remila.sex.cirrus.mesh.FaceVisibilityCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
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
    private static boolean cirrus$faceVisibility(BlockState state, BlockGetter world, BlockPos pos,
            Direction direction, BlockPos neighborPos, Operation<Boolean> original) {
        if (!FaceVisibility.isEnabled()) return original.call(state, world, pos, direction, neighborPos);
        BlockState neighbor = world.getBlockState(neighborPos);
        // Position-dependent shapes retain vanilla's implementation and its integration points.
        if (state.getBlock().hasDynamicShape() || neighbor.getBlock().hasDynamicShape()) {
            return original.call(state, world, pos, direction, neighborPos);
        }
        if (state.skipRendering(neighbor, direction)) return false;
        if (!neighbor.canOcclude()) return true;
        FaceVisibilityCache cache = cirrus$faces.get();
        int face = direction.ordinal();
        int cached = cache.get(state, neighbor, face);
        if (cached != FaceVisibilityCache.MISS) return cached == 1;
        VoxelShape shape = state.getFaceOcclusionShape(world, pos, direction);
        if (shape.isEmpty()) return true;
        VoxelShape other = neighbor.getFaceOcclusionShape(world, neighborPos, direction.getOpposite());
        boolean visible = Shapes.joinIsNotEmpty(shape, other, BooleanOp.ONLY_FIRST);
        cache.put(state, neighbor, face, visible);
        return visible;
    }
}
