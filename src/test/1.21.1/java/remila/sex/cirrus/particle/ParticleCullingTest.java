package remila.sex.cirrus.particle;

import java.util.Random;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParticleCullingTest {
    @Test void conservativeBoundsContainRotatedBillboardCorners() {
        Random random = new Random(1234);
        for (int i = 0; i < 10000; i++) {
            Quaternionf rotation = new Quaternionf().rotationXYZ(random.nextFloat() * 6.28f,
                    random.nextFloat() * 6.28f, random.nextFloat() * 6.28f);
            float size = (random.nextFloat() - 0.5f) * 1000;
            double radius = ParticleCulling.radius(size);
            for (int x : new int[]{-1, 1}) for (int y : new int[]{-1, 1}) {
                Vector3f corner = new Vector3f(x, y, 0).rotate(rotation).mul(size);
                assertTrue(Math.abs(corner.x) <= radius);
                assertTrue(Math.abs(corner.y) <= radius);
                assertTrue(Math.abs(corner.z) <= radius);
            }
        }
    }

    @Test void keepsLargeBillboardsWhoseCentersAreOutsideTheView() {
        Frustum frustum = new Frustum(new Matrix4f(), new Matrix4f().perspective(
                (float) Math.toRadians(70), 16f / 9, 0.05f, 256));
        frustum.prepare(0, 0, 0);
        assertFalse(frustum.isVisible(new AABB(18, -0.1, -10, 18.1, 0.1, -9.9)));
        double radius = ParticleCulling.radius(5);
        assertTrue(frustum.isVisible(new AABB(18 - radius, -radius, -10 - radius,
                18 + radius, radius, -10 + radius)));
        assertFalse(frustum.isVisible(new AABB(-radius, -radius, 50 - radius,
                radius, radius, 50 + radius)));
    }

    @Test void retainsPaddingForZeroSizeAndSignIndependentSize() {
        assertEquals(0.01, ParticleCulling.radius(0));
        assertEquals(ParticleCulling.radius(5), ParticleCulling.radius(-5));
    }
}
