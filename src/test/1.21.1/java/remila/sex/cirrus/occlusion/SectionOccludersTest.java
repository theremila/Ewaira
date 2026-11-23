package remila.sex.cirrus.occlusion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SectionOccludersTest {
    @Test void fullSectionKeepsEveryBitAndRejectsRecycledSectionCoordinates() {
        SectionOccluders.Builder builder = new SectionOccluders.Builder(-2, -4, 3);
        for (int y = -64; y < -48; y++) for (int z = 48; z < 64; z++) for (int x = -32; x < -16; x++) builder.mark(x, y, z);
        SectionOccluders snapshot = builder.build();
        for (int y = -64; y < -48; y++) for (int z = 48; z < 64; z++) for (int x = -32; x < -16; x++) assertTrue(snapshot.contains(x, y, z));
        assertFalse(snapshot.contains(-16, -64, 48));
        assertFalse(snapshot.contains(-32, -48, 48));
        assertFalse(snapshot.contains(-32, -64, 64));
        assertThrows(IllegalArgumentException.class, () -> builder.mark(0, 0, 0));
    }
    @Test void publishedSnapshotDoesNotShareMutableBuilderStorage() {
        SectionOccluders.Builder builder = new SectionOccluders.Builder(0, 0, 0);
        builder.mark(0, 0, 0);
        SectionOccluders first = builder.build();
        builder.mark(15, 15, 15);
        SectionOccluders second = builder.build();
        assertTrue(first.contains(0, 0, 0));
        assertFalse(first.contains(15, 15, 15));
        assertTrue(second.contains(15, 15, 15));
    }
}
