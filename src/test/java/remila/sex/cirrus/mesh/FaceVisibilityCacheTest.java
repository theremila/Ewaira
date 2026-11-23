package remila.sex.cirrus.mesh;

import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FaceVisibilityCacheTest {
    @Test void distinguishesIdentityDirectionAndOrder() {
        FaceVisibilityCache cache = new FaceVisibilityCache();
        Object a = new String("same"), b = new String("same");
        assertEquals(FaceVisibilityCache.MISS, cache.get(a, b, 0));
        cache.put(a, b, 0, true);
        assertEquals(1, cache.get(a, b, 0));
        assertEquals(FaceVisibilityCache.MISS, cache.get(b, a, 0));
        assertEquals(FaceVisibilityCache.MISS, cache.get(a, b, 1));
        cache.put(a, b, 0, false);
        assertEquals(0, cache.get(a, b, 0));
    }
    @Test void collisionsNeverReturnAnotherPairsValue() {
        FaceVisibilityCache cache = new FaceVisibilityCache();
        Object[] states = new Object[512];
        for (int i = 0; i < states.length; i++) states[i] = new Object();
        Random random = new Random(44);
        for (int i = 0; i < 100000; i++) {
            int a = random.nextInt(states.length), b = random.nextInt(states.length), face = random.nextInt(6);
            boolean visible = ((a + b + face) & 1) == 0;
            int value = cache.get(states[a], states[b], face);
            assertTrue(value == FaceVisibilityCache.MISS || value == (visible ? 1 : 0));
            cache.put(states[a], states[b], face, visible);
            assertEquals(visible ? 1 : 0, cache.get(states[a], states[b], face));
        }
    }
}
