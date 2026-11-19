package dev.evasex.ewaira.verification;

import dev.evasex.ewaira.worldgen.SurfaceDensityBuffers;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;

public final class SurfaceDensityVerification {
    private SurfaceDensityVerification() {}

    public static String verify() {
        SurfaceDensityBuffers buffers = new SurfaceDensityBuffers();
        buffers.enterSurface();
        DensityBuffer first = lease(buffers, 98_304);
        DensityBuffer second = lease(buffers, 98_304);
        check(first != null && second != null && first != second, "Live evaluators share a density buffer");
        first.set(first.capacity() - 1, 42);
        second.set(0, -42);

        buffers.enterSurface();
        check(!buffers.canEnterOre(), "Nested surface should use the vanilla buffer");
        buffers.leaveSurface();
        check(lease(buffers, 262_144) == null, "Oversized buffer retained");
        buffers.leaveSurface();

        buffers.enterSurface();
        check(lease(buffers, first.capacity()) == first && first.get(first.capacity() - 1) == 0,
                "Previously used buffer was not reset");
        check(lease(buffers, second.capacity()) == second && second.get(0) == 0, "Second buffer was not reset");
        buffers.leaveSurface();
        check(!buffers.canEnterOre(), "Lease permitted outside surface generation");
        return "PASS: density buffers have exact capacity, remain distinct within a surface, reset on reuse and bypass nested or oversized requests.\n";
    }

    private static DensityBuffer lease(SurfaceDensityBuffers buffers, int size) {
        check(buffers.canEnterOre(), "Ore scope unavailable inside surface generation");
        buffers.enterOre();
        try {
            DensityBuffer result = buffers.acquire(size);
            check(buffers.acquire(size) == null, "Repeated allocation reused the outer request");
            return result;
        } finally {
            buffers.leaveOre();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
