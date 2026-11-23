package remila.sex.cirrus.memory;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

class NativeArenaTest {
    @Test
    void bumpAllocationRespectsAlignmentAndReleasesExactBytes() {
        long baselineBytes = NativeArena.liveBytes();
        long baselineAllocs = NativeArena.liveAllocations();
        try (var arena = new NativeArena(128, 256)) {
            assertTrue(NativeArena.liveBytes() >= baselineBytes + 128);
            assertEquals(baselineAllocs + 1, NativeArena.liveAllocations());

            long a = arena.allocate(5, 16);
            assertEquals(0L, a & 15L);
            MemoryUtil.memPutInt(a, 0xCAFEBABE);

            long b = arena.allocate(20, 16);
            assertEquals(0L, b & 15L);
            MemoryUtil.memPutLong(b, 0x123456789ABCDEF0L);

            long c = arena.allocate(512, 64);
            assertEquals(0L, c & 63L);
            MemoryUtil.memPutInt(c + 508, 0x0BADF00D);
            assertEquals(0xCAFEBABE, MemoryUtil.memGetInt(arena.address()));
            assertEquals(0x123456789ABCDEF0L, MemoryUtil.memGetLong(arena.address() + 16));
            assertEquals(0x0BADF00D, MemoryUtil.memGetInt(c + 508));

            long grownCapacity = arena.capacity();
            assertTrue(grownCapacity >= 576);
            arena.reset();
            assertEquals(0L, arena.used());
            assertEquals(256L, arena.capacity());
        }
        assertEquals(baselineBytes, NativeArena.liveBytes());
        assertEquals(baselineAllocs, NativeArena.liveAllocations());
    }

    @Test
    void closedArenaRejectsFurtherUse() {
        var arena = new NativeArena(64);
        arena.close();
        assertTrue(arena.isClosed());
        assertThrows(IllegalStateException.class, () -> arena.ensure(32));
        arena.close(); // Idempotent
    }
}
