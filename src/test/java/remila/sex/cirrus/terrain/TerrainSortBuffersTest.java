package remila.sex.cirrus.terrain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TerrainSortBuffersTest {
    @Test void retainsOnlyBoundedScratchAndGrowsForLargerSections() {
        TerrainSortBuffers buffers = new TerrainSortBuffers();
        byte[] small = buffers.snapshot(1000);
        assertSame(small, buffers.snapshot(1));
        byte[] larger = buffers.snapshot(4097);
        assertNotSame(small, larger);
        assertTrue(larger.length >= 4097);
        assertSame(larger, buffers.snapshot(4096));
        assertEquals((1 << 20) + 1, buffers.snapshot((1 << 20) + 1).length);
        assertSame(larger, buffers.snapshot(4097));
        byte[] buckets = buffers.buckets(4000);
        assertSame(buckets, buffers.buckets(1));
        assertEquals((1 << 15) + 1, buffers.buckets((1 << 15) + 1).length);
        assertSame(buckets, buffers.buckets(4000));
    }

}
