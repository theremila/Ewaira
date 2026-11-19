package dev.evasex.ewaira.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FarTerrainLodTest {
    @Test void framebufferHeightAndFovChangeWhichTerrainDetailFits() {
        int small = FarTerrainLod.focalPixels(854, 480, 1.5f);
        int large = FarTerrainLod.focalPixels(1708, 960, 1.5f);
        int zoomed = FarTerrainLod.focalPixels(854, 480, 3.0f);

        assertEquals(360, small);
        assertEquals(small * 2, large);
        assertEquals(large, zoomed);
        assertTrue(FarTerrainLod.fits(16, 2, 500, small));
        assertFalse(FarTerrainLod.fits(16, 2, 500, large));
        assertTrue(FarTerrainLod.fits(8, 1, 500, large));
        assertFalse(FarTerrainLod.fits(8, 2, 500, zoomed));
        assertTrue(FarTerrainLod.fits(16, 2, 1_000, large));
    }

    @Test void minimizedOrInvalidViewportDefersRequests() {
        assertEquals(0, FarTerrainLod.focalPixels(0, 480, 1.0f));
        assertEquals(0, FarTerrainLod.focalPixels(854, 0, 1.0f));
        assertEquals(0, FarTerrainLod.focalPixels(854, 480, Float.NaN));
    }
}
