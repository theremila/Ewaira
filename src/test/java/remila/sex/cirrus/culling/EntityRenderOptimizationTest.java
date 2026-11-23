package remila.sex.cirrus.culling;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EntityRenderOptimizationTest {
    @AfterEach
    void restore() {
        EntityOcclusionEngine.setEnabled(true);
        EntityRenderOptimization.beginFrame();
    }

    @Test
    void shadowDistanceAndCellCrowdLimitsResetEveryFrame() {
        EntityRenderOptimization.beginFrame();
        // Beyond 24 blocks -> always skipped.
        assertFalse(EntityRenderOptimization.shouldExtractShadow(10.0, 10.0, 25.0 * 25.0));

        // Within 24 blocks, up to MAX_SHADOWS_PER_CELL entities in the same 2x2 cell extract shadows.
        for (int i = 0; i < EntityRenderOptimization.MAX_SHADOWS_PER_CELL; i++) {
            assertTrue(EntityRenderOptimization.shouldExtractShadow(10.2, 10.7, 64.0));
        }
        assertFalse(EntityRenderOptimization.shouldExtractShadow(10.9, 10.1, 64.0));

        // Different 2x2 cell still gets its own shadow budget.
        assertTrue(EntityRenderOptimization.shouldExtractShadow(14.0, 10.0, 64.0));

        // Next frame resets the budget in O(1).
        EntityRenderOptimization.beginFrame();
        assertTrue(EntityRenderOptimization.shouldExtractShadow(10.2, 10.7, 64.0));
    }

    @Test
    void disablingOptimizationRestoresAllShadows() {
        EntityOcclusionEngine.setEnabled(false);
        assertTrue(EntityRenderOptimization.shouldExtractShadow(10.0, 10.0, 9999.0));
    }
}
