package remila.sex.cirrus.worldgen;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;

/** Scratch for native ore rule evaluators, retained only on their generating worker. */
public final class SurfaceDensityBuffers {
    private static final int MAX_BUFFERS = 4;
    private static final int MAX_FLOATS_PER_BUFFER = 131_072;
    private static final ThreadLocal<SurfaceDensityBuffers> WORKER = ThreadLocal.withInitial(SurfaceDensityBuffers::new);

    private final DensityBuffer[] buffers = new DensityBuffer[MAX_BUFFERS];
    private int surfaceDepth;
    private boolean evaluatingOre;
    private boolean allocationPending;
    private int used;

    public static SurfaceDensityBuffers worker() {
        return WORKER.get();
    }

    public void enterSurface() {
        if (surfaceDepth++ == 0) used = 0;
    }

    public void leaveSurface() {
        surfaceDepth--;
    }

    public boolean canEnterOre() {
        return surfaceDepth == 1 && !evaluatingOre;
    }

    public void enterOre() {
        evaluatingOre = true;
        allocationPending = true;
    }

    public void leaveOre() {
        allocationPending = false;
        evaluatingOre = false;
    }

    public DensityBuffer acquire(int size) {
        if (surfaceDepth != 1 || !allocationPending) return null;
        allocationPending = false;
        if (used == buffers.length || size <= 0 || size > MAX_FLOATS_PER_BUFFER) return null;

        // Match by exact capacity. Slots below used still belong to live evaluators.
        for (int slot = used; slot < buffers.length; slot++) {
            DensityBuffer buffer = buffers[slot];
            if (buffer != null && buffer.capacity() == size) {
                buffers[slot] = buffers[used];
                buffers[used++] = buffer;
                buffer.fill(0.0f);
                return buffer;
            }
        }
        DensityBuffer buffer = DensityBuffer.createUnpooled(size);
        buffers[used++] = buffer;
        return buffer;
    }
}
