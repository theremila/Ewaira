package dev.evasex.ewaira.mesh;

import java.util.Arrays;

/** Bounded, per-builder cache. Keys are validated on every hit; collisions only cause recomputation. */
public final class SectionLightingCache {
    public static final int LIGHT_MISS = Integer.MAX_VALUE;
    private static final int CAPACITY = 8192;
    private final long[] positions = new long[CAPACITY];
    private final int[] lights = new int[CAPACITY];
    private final float[] shades = new float[CAPACITY];
    private final int[] generations = new int[CAPACITY];
    private final byte[] populated = new byte[CAPACITY];
    private int generation = 1;

    static { ChunkLighting.markAvailable(); }

    public int getLight(long position) {
        int slot = slot(position);
        return matches(slot, position) && (populated[slot] & 1) != 0 ? lights[slot] : LIGHT_MISS;
    }

    public float getShade(long position) {
        int slot = slot(position);
        return matches(slot, position) && (populated[slot] & 2) != 0 ? shades[slot] : Float.NaN;
    }

    public void putLight(long position, int light) {
        int slot = prepare(position);
        lights[slot] = light;
        populated[slot] |= 1;
    }

    public void putShade(long position, float shade) {
        int slot = prepare(position);
        shades[slot] = shade;
        populated[slot] |= 2;
    }

    public void clear() {
        if (++generation == 0) {
            Arrays.fill(generations, 0);
            generation = 1;
        }
    }

    private int prepare(long position) {
        int slot = slot(position);
        if (!matches(slot, position)) {
            positions[slot] = position;
            generations[slot] = generation;
            populated[slot] = 0;
        }
        return slot;
    }

    private boolean matches(int slot, long position) {
        return generations[slot] == generation && positions[slot] == position;
    }

    private static int slot(long position) {
        long hash = position * 0x9E3779B97F4A7C15L;
        hash ^= hash >>> 32;
        return (int) (hash ^ (hash >>> 16)) & (CAPACITY - 1);
    }
}
