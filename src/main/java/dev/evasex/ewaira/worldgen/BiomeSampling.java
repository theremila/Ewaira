package dev.evasex.ewaira.worldgen;

import dev.evasex.ewaira.ModIdentity;

/** Worker-local, bounded jitter table. Keys include seed and full quart coordinates. */
public final class BiomeSampling {
    private static volatile boolean enabled = !ModIdentity.disabledBySystemProperty();
    private static final ThreadLocal<Jitter> TABLE = ThreadLocal.withInitial(Jitter::new);
    private BiomeSampling() {}
    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }
    public static int corner(long seed, int blockX, int blockY, int blockZ) {
        int x = blockX - 2, y = blockY - 2, z = blockZ - 2;
        int qx = x >> 2, qy = y >> 2, qz = z >> 2;
        double dx = (x & 3) / 4.0, dy = (y & 3) / 4.0, dz = (z & 3) / 4.0;
        Jitter table = TABLE.get();
        int winner = 0;
        double closest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 8; i++) {
            int ox = (i & 4) == 0 ? 0 : 1, oy = (i & 2) == 0 ? 0 : 1, oz = (i & 1) == 0 ? 0 : 1;
            int slot = table.slot(seed, qx + ox, qy + oy, qz + oz);
            double a = dz - oz + table.jz[slot], b = dy - oy + table.jy[slot], c = dx - ox + table.jx[slot];
            double distance = a * a + b * b + c * c;
            if (closest > distance) { closest = distance; winner = i; }
        }
        return winner;
    }
    private static long next(long value, long salt) { return value * (value * 6364136223846793005L + 1442695040888963407L) + salt; }
    private static double fiddle(long value) { return (Math.floorMod(value >> 24, 1024) / 1024.0 - 0.5) * 0.9; }
    private static final class Jitter {
        private final long[] seeds = new long[1024];
        private final int[] xs = new int[1024], ys = new int[1024], zs = new int[1024];
        private final boolean[] valid = new boolean[1024];
        private final double[] jx = new double[1024], jy = new double[1024], jz = new double[1024];
        int slot(long seed, int x, int y, int z) {
            int slot = (x * 73428767 ^ y * 912931 ^ z * 4382893) & 1023;
            if (!valid[slot] || seeds[slot] != seed || xs[slot] != x || ys[slot] != y || zs[slot] != z) {
                long state = next(next(next(next(next(next(seed, x), y), z), x), y), z);
                jx[slot] = fiddle(state);
                state = next(state, seed); jy[slot] = fiddle(state);
                state = next(state, seed); jz[slot] = fiddle(state);
                seeds[slot] = seed; xs[slot] = x; ys[slot] = y; zs[slot] = z; valid[slot] = true;
            }
            return slot;
        }
    }
}
