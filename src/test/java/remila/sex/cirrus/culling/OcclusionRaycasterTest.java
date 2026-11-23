package remila.sex.cirrus.culling;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.SectionPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OcclusionRaycasterTest {
    @AfterEach
    void cleanup() {
        SectionOccupancyMap.clear();
        EntityOcclusionEngine.invalidateCache();
    }

    @Test
    void solidWallOccludesTargetBehindItWhileSingleGapExposesIt() {
        long[] wall = new long[64];
        // Fill a 5x5 solid wall at x=5, y=0..4, z=0..4 inside section (0,0,0)
        for (int y = 0; y <= 4; y++) {
            for (int z = 0; z <= 4; z++) {
                int bit = 5 | (z << 4) | (y << 8);
                wall[bit >>> 6] |= 1L << (bit & 63);
            }
        }
        long key = SectionPos.asLong(0, 0, 0);
        SectionOccupancyMap.put(key, wall);

        // Camera at (1, 2.5, 2.5), target box at (8..9, 1.5..3.0, 2.0..3.0) directly behind the 5x5 wall.
        assertTrue(OcclusionRaycaster.isBoxOccluded(1.0, 2.5, 2.5, 8.0, 1.5, 2.0, 9.0, 3.0, 3.0,
                SectionOccupancyMap::isOpaque));

        // Punch a 1-block window at (5, 2, 2): center ray passes right through -> MUST be visible!
        int windowBit = 5 | (2 << 4) | (2 << 8);
        wall[windowBit >>> 6] &= ~(1L << (windowBit & 63));
        SectionOccupancyMap.put(key, wall);

        assertFalse(OcclusionRaycaster.isBoxOccluded(1.0, 2.5, 2.5, 8.0, 1.5, 2.0, 9.0, 3.0, 3.0,
                SectionOccupancyMap::isOpaque));
    }

    @Test
    void cameraInsideOrAdjacentToBoxNeverCulls() {
        SectionOccupancyMap.put(SectionPos.asLong(0, 0, 0), SectionOccupancyMap.ALL_SOLID);
        assertFalse(OcclusionRaycaster.isBoxOccluded(2.0, 2.0, 2.0, 1.5, 1.5, 1.5, 2.5, 2.5, 2.5,
                SectionOccupancyMap::isOpaque));
    }

    @Test
    void columnEvictionRemovesAllSectionMasks() {
        SectionOccupancyMap.put(SectionPos.asLong(3, 0, -2), SectionOccupancyMap.ALL_SOLID);
        SectionOccupancyMap.put(SectionPos.asLong(3, 1, -2), SectionOccupancyMap.ALL_SOLID);
        assertTrue(SectionOccupancyMap.isOpaque(48, 5, -32));
        assertTrue(SectionOccupancyMap.isOpaque(48, 20, -32));

        SectionOccupancyMap.removeColumn(3, -2, 0, 1);
        assertFalse(SectionOccupancyMap.isOpaque(48, 5, -32));
        assertFalse(SectionOccupancyMap.isOpaque(48, 20, -32));
        assertEquals(0, SectionOccupancyMap.size());
    }
}
