package dev.evasex.ewaira.verification;

import com.sun.management.ThreadMXBean;
import dev.evasex.ewaira.worldgen.PalettePacking;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.stream.LongStream;
import net.minecraft.core.IdMapper;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO.PackedData;
import net.minecraft.world.level.chunk.Strategy;

public final class PalettePackingVerification {
    private static final int REGISTRY_SIZE = 321;
    private static final int PACKS = 512;
    private static volatile PackedData<Integer> measuredPack;

    private PalettePackingVerification() {}

    public static String verify() {
        IdMapper<Integer> registry = new IdMapper<>(REGISTRY_SIZE);
        for (int i = 0; i < REGISTRY_SIZE; i++) registry.add(i);
        Strategy<Integer> blocks = Strategy.createForBlockStates(registry);
        Strategy<Integer> biomes = Strategy.createForBiomes(registry);
        boolean previous = PalettePacking.isEnabled();
        try {
            for (int values : new int[]{1, 7, 17, 64, 300}) {
                verifyPacking(blocks, 16, values);
                verifyPacking(biomes, 4, Math.min(values, 64));
            }
            PalettedContainer<Integer> container = fixture(blocks, 16, 7);
            allocatedBytes(container, blocks, false);
            allocatedBytes(container, blocks, true);
            long nativeBytes = allocatedBytes(container, blocks, false);
            long optimizedBytes = allocatedBytes(container, blocks, true);
            if (nativeBytes - optimizedBytes < 15_000) {
                throw new AssertionError("Packing did not reuse its section-sized array");
            }
            return "PASS: palette storage bytes and decoded blocks/biomes match native for single, local and global palettes.\n"
                    + "palette_pack_allocated_bytes_per_block_section: native=" + nativeBytes + ", optimized=" + optimizedBytes + "\n";
        } finally {
            PalettePacking.setEnabled(previous);
        }
    }

    private static void verifyPacking(Strategy<Integer> strategy, int size, int values) {
        PalettedContainer<Integer> fixture = fixture(strategy, size, values);
        PalettePacking.setEnabled(false);
        PackedData<Integer> nativePack = fixture.pack(strategy);
        PalettePacking.setEnabled(true);
        PackedData<Integer> optimizedPack = fixture.pack(strategy);
        if (nativePack.bitsPerEntry() != optimizedPack.bitsPerEntry()
                || !nativePack.paletteEntries().equals(optimizedPack.paletteEntries())
                || !Arrays.equals(nativePack.storage().orElseGet(LongStream::empty).toArray(),
                                  optimizedPack.storage().orElseGet(LongStream::empty).toArray())) {
            throw new AssertionError("Different encoded palette, size " + size + ", values " + values);
        }

        PalettePacking.setEnabled(false);
        PackedData<Integer> toDecode = fixture.pack(strategy);
        PalettePacking.setEnabled(true);
        PalettedContainer<Integer> decoded = PalettedContainer.unpack(strategy, toDecode).getOrThrow();
        for (int y = 0; y < size; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    if (decoded.get(x, y, z) != fixture.get(x, y, z)) {
                        throw new AssertionError("Decoded a different registry entry");
                    }
                }
            }
        }
    }

    private static PalettedContainer<Integer> fixture(Strategy<Integer> strategy, int size, int values) {
        PalettedContainer<Integer> container = new PalettedContainer<>(strategy.globalMap().byId(0), strategy);
        int next = 0;
        for (int y = 0; y < size; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    container.set(x, y, z, strategy.globalMap().byId(next++ % values));
                }
            }
        }
        return container;
    }

    private static long allocatedBytes(PalettedContainer<Integer> container, Strategy<Integer> strategy, boolean enabled) {
        PalettePacking.setEnabled(enabled);
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long threadId = Thread.currentThread().threadId();
        long start = bean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < PACKS; i++) measuredPack = container.pack(strategy);
        return (bean.getThreadAllocatedBytes(threadId) - start) / PACKS;
    }
}
