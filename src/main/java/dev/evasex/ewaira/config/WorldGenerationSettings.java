package dev.evasex.ewaira.config;

import dev.evasex.ewaira.worldgen.BiomeSampling;
import dev.evasex.ewaira.worldgen.DensityBufferReuse;
import dev.evasex.ewaira.worldgen.PalettePacking;

public final class WorldGenerationSettings {
    private WorldGenerationSettings() {}

    public static void apply(EwairaSettings settings, boolean allowed) {
        BiomeSampling.setEnabled(allowed && settings.active(Optimization.BIOME_SAMPLING));
        PalettePacking.setEnabled(allowed && settings.active(Optimization.PALETTE_PACKING));
        DensityBufferReuse.setEnabled(allowed && settings.active(Optimization.DENSITY_BUFFERS));
    }
}
