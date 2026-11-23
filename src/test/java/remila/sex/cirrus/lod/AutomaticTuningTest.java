package remila.sex.cirrus.lod;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AutomaticTuningTest {
    @Test void scalesDetailWithProjectedSizeAndCapsToLoadedDistance() {
        assertEquals(new AutomaticTuning.Distances(64, 32), AutomaticTuning.distances(32, 720, 70));
        assertEquals(new AutomaticTuning.Distances(128, 64), AutomaticTuning.distances(32, 1440, 70));
        assertTrue(AutomaticTuning.distances(32, 720, 40).terrain() > 64);
        assertTrue(AutomaticTuning.distances(32, 720, 110).terrain() < 64);
        assertEquals(32, AutomaticTuning.distances(2, 2160, 70).terrain());
        assertEquals(512, AutomaticTuning.distances(64, Integer.MAX_VALUE, 10).terrain());
        assertEquals(128, AutomaticTuning.distances(64, Integer.MAX_VALUE, 10).items());
    }
    @Test void invalidProjectionUsesStableDefaults() {
        assertEquals(new AutomaticTuning.Distances(64, 32), AutomaticTuning.distances(32, 0, 70));
        assertEquals(new AutomaticTuning.Distances(64, 32), AutomaticTuning.distances(32, 720, Double.NaN));
    }
}
