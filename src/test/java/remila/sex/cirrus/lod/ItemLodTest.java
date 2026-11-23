package remila.sex.cirrus.lod;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ItemLodTest {
    @AfterEach void reset() { ItemLod.setEnabled(true); ItemLod.setDistance(32); ItemLod.restore(Double.NaN); }
    @Test void thresholdsPreserveNearAndNeverIncreaseCopies() {
        ItemLod.setEnabled(true); ItemLod.setDistance(32);
        for (int n = 0; n <= 5; n++) {
            assertEquals(n, ItemLod.count(n, 255));
            assertEquals(Math.min(n, 2), ItemLod.count(n, 256));
            assertEquals(Math.min(n, 1), ItemLod.count(n, 1024));
        }
    }
    @Test void invalidAndDisabledDistancesKeepVanilla() {
        for (double d : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1}) assertEquals(5, ItemLod.count(5, d));
        ItemLod.setEnabled(false); assertEquals(5, ItemLod.count(5, 10000));
        assertThrows(IllegalArgumentException.class, () -> ItemLod.setDistance(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> ItemLod.setDistance(7));
    }
    @Test void nestedLegacyScopesRestoreAfterFailure() {
        assertEquals(5, ItemLod.scopedCount(5));
        double outer = ItemLod.enter(1024);
        try {
            assertEquals(1, ItemLod.scopedCount(5));
            double inner = ItemLod.enter(0);
            try { assertEquals(5, ItemLod.scopedCount(5)); }
            finally { ItemLod.restore(inner); }
            assertEquals(1, ItemLod.scopedCount(5));
        } finally { ItemLod.restore(outer); }
        assertEquals(5, ItemLod.scopedCount(5));
    }
    @Test void configuredDistanceMovesBothBoundaries() {
        ItemLod.setDistance(64);
        assertEquals(5, ItemLod.count(5, 1023));
        assertEquals(2, ItemLod.count(5, 1024));
        assertEquals(1, ItemLod.count(5, 4096));
    }
}
