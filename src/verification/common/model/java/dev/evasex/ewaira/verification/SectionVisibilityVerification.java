package dev.evasex.ewaira.verification;

import com.sun.management.ThreadMXBean;
import dev.evasex.ewaira.mesh.SectionVisibility;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.BitSet;
import java.util.Random;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Compares the transformed native VisGraph against its disabled original method. */
final class SectionVisibilityVerification {
    private SectionVisibilityVerification() {}

    static String verify() throws ReflectiveOperationException {
        Field bits = VisGraph.class.getDeclaredField("bitSet");
        bits.setAccessible(true);
        Random random = new Random(29093);
        ThreadMXBean allocations = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        allocations.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        long vanillaBytes = 0, optimizedBytes = 0;
        Direction[] directions = Direction.values();
        try {
            for (int sample = 0; sample < 240; sample++) {
                VisGraph vanilla = new VisGraph(), optimized = new VisGraph();
                for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    boolean opaque = switch (sample % 8) {
                        case 0 -> false;
                        case 1 -> true;
                        case 2 -> x == 8;
                        case 3 -> ((x + y + z) & 1) == 0;
                        default -> random.nextDouble() < (sample % 8 - 3) * 0.2;
                    };
                    if (opaque) {
                        BlockPos pos = new BlockPos(x, y, z);
                        vanilla.setOpaque(pos); optimized.setOpaque(pos);
                    }
                }
                SectionVisibility.setEnabled(false);
                long before = allocations.getThreadAllocatedBytes(thread);
                VisibilitySet expected = vanilla.resolve();
                long oldBytes = allocations.getThreadAllocatedBytes(thread) - before;
                SectionVisibility.setEnabled(true);
                before = allocations.getThreadAllocatedBytes(thread);
                VisibilitySet actual = optimized.resolve();
                long newBytes = allocations.getThreadAllocatedBytes(thread) - before;
                if (sample >= 40) { vanillaBytes += oldBytes; optimizedBytes += newBytes; }
                for (Direction from : directions) for (Direction to : directions) {
                    if (expected.visibilityBetween(from, to) != actual.visibilityBetween(from, to)) {
                        throw new AssertionError("Section visibility mismatch in sample " + sample + ": " + from + " -> " + to);
                    }
                }
                if (!((BitSet) bits.get(vanilla)).equals(bits.get(optimized))) {
                    throw new AssertionError("Flood fill changed native visited-cell state");
                }
            }
        } finally { SectionVisibility.setEnabled(true); }
        if (optimizedBytes >= vanillaBytes) throw new AssertionError("Visibility allocation regression: " + vanillaBytes + " -> " + optimizedBytes);
        return "PASS: 240 native section visibility graphs, all 36 face pairs and visited cells match vanilla.\n"
                + "section_visibility_allocated_bytes_200_resolves: disabled=" + vanillaBytes + ", enabled=" + optimizedBytes + "\n"
                + "Synthetic section compilation, same JVM and injected entry hook. Not gameplay GC pause or FPS evidence.\n";
    }
}
