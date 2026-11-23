package remila.sex.cirrus.config;

import remila.sex.cirrus.worldgen.BiomeSampling;
import remila.sex.cirrus.worldgen.DensityBufferReuse;
import remila.sex.cirrus.worldgen.PalettePacking;

public final class WorldGenerationSettings {
    private WorldGenerationSettings() {}

    public static void apply(CirrusSettings settings, boolean allowed) {
        BiomeSampling.setEnabled(allowed && settings.active(Optimization.BIOME_SAMPLING));
        PalettePacking.setEnabled(allowed && settings.active(Optimization.PALETTE_PACKING));
        DensityBufferReuse.setEnabled(allowed && settings.active(Optimization.DENSITY_BUFFERS));
    }
}
