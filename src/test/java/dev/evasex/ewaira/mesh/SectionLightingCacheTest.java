package dev.evasex.ewaira.mesh;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SectionLightingCacheTest {
    @Test void keepsIndependentLightAndShadeValuesForOnePosition() {
        SectionLightingCache cache = new SectionLightingCache();
        assertEquals(SectionLightingCache.LIGHT_MISS, cache.getLight(0));
        assertTrue(Float.isNaN(cache.getShade(0)));
        cache.putLight(0, 0xF000F0);
        assertTrue(Float.isNaN(cache.getShade(0)));
        cache.putShade(0, -0.0f);
        assertEquals(0xF000F0, cache.getLight(0));
        assertEquals(Float.floatToRawIntBits(-0.0f), Float.floatToRawIntBits(cache.getShade(0)));
    }

    @Test void collisionsNeverReturnAnotherPositionsLightOrShade() {
        SectionLightingCache cache = new SectionLightingCache();
        for (int i = 0; i < 100000; i++) {
            long key = (long) i << 38 | i;
            cache.putLight(key, i);
            cache.putShade(key, i / 100000f);
        }
        for (int i = 0; i < 100000; i++) {
            long key = (long) i << 38 | i;
            int light = cache.getLight(key);
            if (light != SectionLightingCache.LIGHT_MISS) assertEquals(i, light);
            float shade = cache.getShade(key);
            if (!Float.isNaN(shade)) assertEquals(i / 100000f, shade);
        }
    }

    @Test void resetsBothChannelsBetweenSectionsIncludingNegativeKeys() {
        SectionLightingCache cache = new SectionLightingCache();
        for (int generation = 0; generation < 20; generation++) {
            assertEquals(SectionLightingCache.LIGHT_MISS, cache.getLight(-1));
            assertTrue(Float.isNaN(cache.getShade(-1)));
            cache.putLight(-1, generation);
            cache.putShade(-1, generation);
            assertEquals(generation, cache.getLight(-1));
            assertEquals(generation, cache.getShade(-1));
            cache.clear();
        }
    }
}
