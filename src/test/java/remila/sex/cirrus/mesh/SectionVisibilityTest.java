package remila.sex.cirrus.mesh;

import static org.junit.jupiter.api.Assertions.*;

import java.util.BitSet;
import org.junit.jupiter.api.Test;

class SectionVisibilityTest {
    @Test void openSectionVisitsEveryCellAndEveryBoundary() {
        BitSet visited = new BitSet(4096);
        assertEquals(63, SectionVisibility.floodFill(visited, 0));
        assertEquals(4096, visited.cardinality());
    }

    @Test void solidWallKeepsOppositeComponentsSeparate() {
        BitSet visited = new BitSet(4096);
        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) visited.set(8 | y << 8 | z << 4);
        assertEquals(63 ^ SectionVisibility.EAST, SectionVisibility.floodFill(visited, 0));
        assertFalse(visited.get(15));
        assertEquals(63 ^ SectionVisibility.WEST, SectionVisibility.floodFill(visited, 15));
        assertEquals(4096, visited.cardinality());
    }

    @Test void enclosedCellDoesNotReachOtherRowsOrSectionBoundaries() {
        BitSet visited = new BitSet(4096);
        visited.set(0, 4096);
        int center = 7 | 7 << 8 | 7 << 4;
        visited.clear(center);
        assertEquals(0, SectionVisibility.floodFill(visited, center));
        assertEquals(4096, visited.cardinality());
        visited.clear(15);
        assertEquals(SectionVisibility.EAST | SectionVisibility.DOWN | SectionVisibility.NORTH,
                SectionVisibility.floodFill(visited, 15));
    }
}
