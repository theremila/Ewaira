package remila.sex.cirrus.occlusion;

import java.util.Random;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoxOcclusionTest {
    @Test void requiresEntireBoxBehindOpaqueVolume() {
        Vec3 eye = new Vec3(0.5, 0.5, 0.5);
        assertTrue(BoxOcclusion.hides(eye, new AABB(0.4, 0.4, 10, 0.6, 0.6, 11), 0, 0, 2));
        assertFalse(BoxOcclusion.hides(eye, new AABB(-3, 0.4, 10, 3, 0.6, 11), 0, 0, 2));
        assertFalse(BoxOcclusion.hides(eye, new AABB(0.4, 0.4, 1.5, 0.6, 0.6, 2.5), 0, 0, 2));
        assertFalse(BoxOcclusion.hides(eye, new AABB(0.4, 0.4, 10, 0.6, 0.6, 11), 0, 0, 0));
        assertFalse(BoxOcclusion.hides(new Vec3(Double.NaN, 0, 0), new AABB(0, 0, 10, 1, 1, 11), 0, 0, 2));
    }
    @Test void handlesBothDirectionsOfEachAxis() {
        Vec3 eye = new Vec3(0.5, 0.5, 0.5);
        for (int sign : new int[]{-1, 1}) {
            int block = sign > 0 ? 2 : -2;
            double low = sign > 0 ? 10 : -11, high = sign > 0 ? 11 : -10;
            assertTrue(BoxOcclusion.hides(eye, new AABB(low, 0.4, 0.4, high, 0.6, 0.6), block, 0, 0));
            assertTrue(BoxOcclusion.hides(eye, new AABB(0.4, low, 0.4, 0.6, high, 0.6), 0, block, 0));
            assertTrue(BoxOcclusion.hides(eye, new AABB(0.4, 0.4, low, 0.6, 0.6, high), 0, 0, block));
        }
    }
    @Test void positiveResultsPassIndependentSegmentSlabReference() {
        Random random = new Random(8851);
        int positives = 0;
        for (int fixture = 0; fixture < 10000; fixture++) {
            Vec3 eye = new Vec3(random.nextDouble(-10, 10), random.nextDouble(-10, 10), random.nextDouble(-10, 10));
            Vec3 center = eye.add(random.nextDouble(-100, 100), random.nextDouble(-100, 100), random.nextDouble(-100, 100));
            double size = random.nextDouble(0.01, 3);
            AABB target = new AABB(center.x - size, center.y - size, center.z - size,
                    center.x + size, center.y + size, center.z + size);
            Vec3 candidate = eye.lerp(center, random.nextDouble(0.02, 0.2));
            int x = (int) Math.floor(candidate.x), y = (int) Math.floor(candidate.y), z = (int) Math.floor(candidate.z);
            if (!BoxOcclusion.hides(eye, target, x, y, z)) continue;
            positives++;
            for (int ix = 0; ix < 3; ix++) for (int iy = 0; iy < 3; iy++) for (int iz = 0; iz < 3; iz++) {
                double[] from = {eye.x, eye.y, eye.z};
                double[] to = {target.minX + (target.maxX - target.minX) * ix / 2,
                        target.minY + (target.maxY - target.minY) * iy / 2,
                        target.minZ + (target.maxZ - target.minZ) * iz / 2};
                int[] block = {x, y, z};
                double entry = 0, exit = 1;
                for (int axis = 0; axis < 3; axis++) {
                    double a = (block[axis] - from[axis]) / (to[axis] - from[axis]);
                    double b = (block[axis] + 1 - from[axis]) / (to[axis] - from[axis]);
                    entry = Math.max(entry, Math.min(a, b)); exit = Math.min(exit, Math.max(a, b));
                }
                assertTrue(entry > 0 && entry < exit && exit < 1, "Unoccluded sample at fixture " + fixture);
            }
        }
        assertTrue(positives > 500, "Fixture must exercise positive results");
    }
    @Test void limitsOccupancyQueriesAndTreatsUnknownCellsAsVisible() {
        Vec3 eye = new Vec3(0.5, 0.5, 0.5);
        AABB far = new AABB(0.4, 0.4, 100, 0.6, 0.6, 101);
        int[] calls = {0};
        assertFalse(BoxOcclusion.hidden(eye, far, (x, y, z) -> { calls[0]++; return false; }));
        assertEquals(8, calls[0]);
        calls[0] = 0;
        assertTrue(BoxOcclusion.hidden(eye, far, (x, y, z) -> { calls[0]++; return true; }));
        assertEquals(1, calls[0]);
        assertFalse(BoxOcclusion.hidden(eye, new AABB(0.4, 0.4, 0.4, 0.6, 0.6, 0.6), (x, y, z) -> true));
    }
}
