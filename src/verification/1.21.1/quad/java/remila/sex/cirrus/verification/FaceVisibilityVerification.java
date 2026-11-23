package remila.sex.cirrus.verification;

import remila.sex.cirrus.mesh.FaceVisibility;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public final class FaceVisibilityVerification {
    private FaceVisibilityVerification() {}

    public static String verify() {
        List<BlockState> states = new ArrayList<>();
        Block[] blocks = {Blocks.AIR, Blocks.STONE, Blocks.GLASS, Blocks.OAK_LEAVES,
                Blocks.OAK_SLAB, Blocks.OAK_STAIRS, Blocks.OAK_FENCE, Blocks.PISTON, Blocks.SNOW,
                Blocks.WATER, Blocks.CHEST, Blocks.IRON_BARS, Blocks.COBBLESTONE_WALL};
        for (Block block : blocks) {
            states.addAll(block.getStateDefinition().getPossibleStates());
        }
        // Bound the sample while retaining all property combinations in the selection pool.
        List<BlockState> sample = new ArrayList<>();
        for (Block block : blocks) sample.add(block.defaultBlockState());
        for (int i = 0; sample.size() < 64; i++) sample.add(states.get(i * states.size() / 51));
        World world = new World();
        int count = 0;
        try {
            for (BlockState state : sample) for (BlockState neighbor : sample) for (Direction direction : Direction.values()) {
                world.state = state;
                world.neighbor = neighbor;
                BlockPos adjacent = world.center.relative(direction);
                FaceVisibility.setEnabled(false);
                boolean expected = Block.shouldRenderFace(state, world, world.center, direction, adjacent);
                FaceVisibility.setEnabled(true);
                boolean actual = Block.shouldRenderFace(state, world, world.center, direction, adjacent);
                if (actual != expected) throw new AssertionError("Face changed: " + state + " / " + neighbor + " / " + direction);
                count++;
            }
            return "PASS: " + count + " face-visibility state pairs/directions match vanilla through Fabric.\n";
        } finally {
            FaceVisibility.setEnabled(true);
        }
    }

    private static final class World implements BlockGetter {
        private final BlockPos center = new BlockPos(-100003, 64, 100007);
        private BlockState state, neighbor;
        public BlockState getBlockState(BlockPos pos) { return pos.equals(center) ? state : neighbor; }
        public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        public int getHeight() { return 384; }
        public int getMinBuildHeight() { return -64; }
    }
}
