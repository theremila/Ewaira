package remila.sex.cirrus.verification;

import remila.sex.cirrus.mesh.FaceVisibility;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class ModernFaceVerification {
    private ModernFaceVerification() {}

    public static String verify() {
        if (Arrays.stream(Block.class.getDeclaredMethods()).noneMatch(method -> method.getName().contains("cirrus$faceVisibility"))) {
            throw new AssertionError("Block face-visibility mixin missing");
        }
        Block[] blocks = {Blocks.AIR, Blocks.STONE, Blocks.GLASS, Blocks.OAK_LEAVES, Blocks.OAK_SLAB,
                Blocks.OAK_STAIRS, Blocks.OAK_FENCE, Blocks.PISTON, Blocks.SNOW, Blocks.WATER,
                Blocks.CHEST, Blocks.IRON_BARS, Blocks.COBBLESTONE_WALL};
        List<BlockState> pool = new ArrayList<>(), sample = new ArrayList<>();
        for (Block block : blocks) {
            pool.addAll(block.getStateDefinition().getPossibleStates());
            sample.add(block.defaultBlockState());
        }
        for (int i = 0; sample.size() < 64; i++) sample.add(pool.get(i * pool.size() / 51));
        int count = 0;
        try {
            for (int pass = 0; pass < 2; pass++) for (BlockState state : sample)
                for (BlockState neighbor : sample) for (Direction direction : Direction.values()) {
                    FaceVisibility.setEnabled(false);
                    boolean expected = Block.shouldRenderFace(state, neighbor, direction);
                    FaceVisibility.setEnabled(true);
                    if (Block.shouldRenderFace(state, neighbor, direction) != expected) {
                        throw new AssertionError("Face mismatch: " + state + " / " + neighbor + " / " + direction);
                    }
                    count++;
                }
            return "PASS: " + count + " face-visibility results match vanilla, including repeated/colliding cache use.\n";
        } finally {
            FaceVisibility.setEnabled(true);
        }
    }
}
