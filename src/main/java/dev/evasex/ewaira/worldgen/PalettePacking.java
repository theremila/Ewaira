package dev.evasex.ewaira.worldgen;

import dev.evasex.ewaira.ModIdentity;

public final class PalettePacking {
    private static volatile boolean enabled = !ModIdentity.disabledBySystemProperty();
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private PalettePacking() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }
    public static Scratch scratch() { return SCRATCH.get(); }

    public static final class Scratch {
        private static final int BIOME_ENTRIES = 64;
        private static final int BLOCK_ENTRIES = 4096;
        private int depth;
        private int[] biomes;
        private int[] blocks;

        private Scratch() {}
        public void enter() { depth++; }
        public void leave() { depth--; }
        public int[] entries(int size) {
            if (depth != 1) return null;
            if (size == BLOCK_ENTRIES) {
                if (blocks == null) blocks = new int[BLOCK_ENTRIES];
                return blocks;
            }
            if (size == BIOME_ENTRIES) {
                if (biomes == null) biomes = new int[BIOME_ENTRIES];
                return biomes;
            }
            return null;
        }
    }
}
