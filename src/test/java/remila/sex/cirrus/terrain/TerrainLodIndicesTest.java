package remila.sex.cirrus.terrain;

import remila.sex.cirrus.lod.TerrainLod;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainLodIndicesTest {
    @AfterEach void restore() {
        TerrainLod.setEnabled(true); TerrainLod.setDistance(64); TerrainOptimization.setEnabled(true);
    }

    @Test void completePlanesReduceFourAndSixteenFoldWithoutChangingVertices() {
        for (int bucket : new int[]{0, 1, 2, 4, 5, 6}) {
            ByteBuffer vertices = plane(bucket, false);
            TerrainFaces faces = TerrainFaces.sort(vertices, 32, 1024);
            byte[] before = vertices.array().clone();
            var result = TerrainLodIndices.build(vertices, 32, 1024);
            assertNotNull(result);
            assertArrayEquals(before, vertices.array());
            assertEquals(256 * 6, result.middleStart());
            assertEquals(64 * 6, result.farStart() - result.middleStart());
            assertEquals(16 * 6, result.count() - result.farStart());
            assertCoverage(vertices, result, 0, result.middleStart(), bucket);
            assertCoverage(vertices, result, result.middleStart(), result.farStart(), bucket);
            assertCoverage(vertices, result, result.farStart(), result.count(), bucket);
            faces.attachLod(result);
            assertTrue(faces.hasLod());
            assertEquals(1536, TerrainFaces.indexCount(faces.range(result.count(), 8, 8, 8, false)));
            assertEquals(384, TerrainFaces.indexCount(faces.range(result.count(), 90, 8, 8, false)));
            assertEquals(96, TerrainFaces.indexCount(faces.range(result.count(), 160, 8, 8, false)));
        }
    }

    @Test void randomizedInputOrderStillMergesCompletePatches() {
        ByteBuffer input = plane(0, false), shuffled = ByteBuffer.allocate(input.capacity()).order(ByteOrder.nativeOrder());
        int[] order = new Random(42).ints(0, 256).distinct().limit(256).toArray();
        for (int q = 0; q < 256; q++) shuffled.put(q * 128, input.array(), order[q] * 128, 128);
        assertNotNull(TerrainFaces.sort(shuffled, 32, 1024));
        var result = TerrainLodIndices.build(shuffled, 32, 1024);
        assertNotNull(result);
        assertEquals(16 * 6, result.count() - result.farStart());
        assertCoverage(shuffled, result, result.farStart(), result.count(), 0);
    }

    @Test void holesAndMaterialBoundariesRemainUnfilled() {
        ByteBuffer vertices = plane(0, false);
        // A missing tile is moved out of the section; a different sprite cuts another patch.
        for (int v = 0; v < 4; v++) vertices.putFloat((5 * 4 + v) * 32, 18);
        vertices.putFloat(32 * 128 + 16, 0.75f);
        var result = TerrainLodIndices.build(vertices, 32, 1024);
        assertNotNull(result);
        // Every missing/foreign tile must survive as its own original quad at both LODs.
        for (int start : new int[]{result.middleStart(), result.farStart()}) {
            int end = start == result.middleStart() ? result.farStart() : result.count();
            assertTrue(hasOriginalQuad(result.indices(), start, end, 5));
            assertTrue(hasOriginalQuad(result.indices(), start, end, 32));
        }
    }

    @Test void duplicateOverlayFacesAreNeverConsumedIntoPatches() {
        ByteBuffer base = plane(0, false);
        ByteBuffer vertices = ByteBuffer.allocate(base.capacity() + 128).order(ByteOrder.nativeOrder());
        vertices.put(0, base.array()); vertices.put(base.capacity(), base.array(), 0, 128);
        var result = TerrainLodIndices.build(vertices, 32, 1028);
        assertNotNull(result);
        assertTrue(hasOriginalQuad(result.indices(), result.farStart(), result.count(), 0));
        assertTrue(hasOriginalQuad(result.indices(), result.farStart(), result.count(), 256));
    }

    @Test void largestSupportedMeshRetainsUnsignedVertexIndices() {
        int vertexCount = 1 << Short.SIZE;
        ByteBuffer vertices = ByteBuffer.allocate(vertexCount * 32).order(ByteOrder.nativeOrder());
        for (int bucket : new int[]{0, 1, 2, 4, 5, 6}) {
            int axis = bucket < 3 ? bucket : 6 - bucket;
            for (int layer = 0; layer < 10; layer++) {
                ByteBuffer plane = plane(bucket, false);
                for (int vertex = 0; vertex < 1024; vertex++) plane.putFloat(vertex * 32 + axis * 4, layer);
                vertices.put(plane.array());
            }
        }
        vertices.clear();
        assertNotNull(TerrainFaces.sort(vertices, 32, vertexCount));
        var result = TerrainLodIndices.build(vertices, 32, vertexCount);
        assertNotNull(result);
        assertTrue(hasOriginalQuad(result.indices(), 0, result.middleStart(), vertexCount / 4 - 1));
    }

    @Test void allDifferentSpritesAndUnsupportedLayoutsBypassLod() {
        ByteBuffer vertices = plane(0, true);
        assertNull(TerrainLodIndices.build(vertices, 32, 1024));
        assertNull(TerrainLodIndices.build(vertices, 24, 1024));
        assertNull(TerrainLodIndices.build(vertices, 32, 1023));
        assertNull(TerrainLodIndices.build(vertices, 32, 4 * 16385));
    }

    @Test void farLevelKeepsSmallerPatchesAroundMaterialBoundaries() {
        ByteBuffer vertices = plane(0, false);
        vertices.putFloat(16, 0.75f);
        var result = TerrainLodIndices.build(vertices, 32, 1024);
        assertNotNull(result);
        assertEquals((15 + 3 + 4) * 6, result.count() - result.farStart());
        assertTrue(hasOriginalQuad(result.indices(), result.farStart(), result.count(), 0));
        assertCoverage(vertices, result, result.farStart(), result.count(), 0);
    }

    @Test void farLevelNeverRestoresFineGeometryWhenOnlyTwoByTwoTilesMatch() {
        for (int bucket : new int[]{0, 1, 2, 4, 5, 6}) {
            ByteBuffer vertices = plane(bucket, false);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int material = (x / 2 + y / 2) % 2;
                for (int v = 0; v < 4; v++) {
                    int at = ((y * 16 + x) * 4 + v) * 32 + 16;
                    vertices.putFloat(at, vertices.getFloat(at) + material * 0.25f);
                }
            }
            var result = TerrainLodIndices.build(vertices, 32, 1024);
            assertNotNull(result);
            assertEquals(64 * 6, result.farStart() - result.middleStart());
            assertEquals(64 * 6, result.count() - result.farStart());
            assertCoverage(vertices, result, result.farStart(), result.count(), bucket);
        }
    }

    @Test void disabledAndInvalidCameraChooseOnlyFullGeometryEvenWithCullingDisabled() {
        ByteBuffer vertices = plane(0, false);
        TerrainFaces faces = TerrainFaces.sort(vertices, 32, 1024);
        var result = TerrainLodIndices.build(vertices, 32, 1024);
        faces.attachLod(result);
        TerrainOptimization.setEnabled(false);
        TerrainLod.setEnabled(false);
        assertEquals(1536, TerrainFaces.indexCount(faces.range(result.count(), 999, 999, 999)));
        TerrainLod.setEnabled(true);
        assertEquals(96, TerrainFaces.indexCount(faces.range(result.count(), 999, 999, 999)));
        assertEquals(1536, TerrainFaces.indexCount(faces.range(result.count(), Double.NaN, 0, 0)));
        assertEquals(0, TerrainFaces.firstIndex(faces.fullRange()));
    }

    @Test void sectionBoundsDistanceAndConfigurableThresholdsAreUsed() {
        assertEquals(0, TerrainLod.level(16 + 63, 8, 8));
        assertEquals(1, TerrainLod.level(16 + 64, 8, 8));
        assertEquals(2, TerrainLod.level(-128, 8, 8));
        TerrainLod.setDistance(128);
        assertEquals(0, TerrainLod.level(-64, 8, 8));
        assertThrows(IllegalArgumentException.class, () -> TerrainLod.setDistance(Double.NaN));
    }

    private static boolean hasOriginalQuad(short[] indices, int start, int end, int q) {
        for (int i = start; i < end; i += 6) if (Short.toUnsignedInt(indices[i]) == q * 4 && Short.toUnsignedInt(indices[i + 1]) == q * 4 + 1
                && Short.toUnsignedInt(indices[i + 2]) == q * 4 + 2 && Short.toUnsignedInt(indices[i + 4]) == q * 4 + 3) return true;
        return false;
    }

    private static ByteBuffer plane(int bucket, boolean distinctMaterials) {
        ByteBuffer vertices = ByteBuffer.allocate(256 * 128).order(ByteOrder.nativeOrder());
        int axis = bucket < 3 ? bucket : 6 - bucket, u = (axis + 1) % 3, w = (axis + 2) % 3;
        int[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) for (int v = 0; v < 4; v++) {
            int q = y * 16 + x, at = (q * 4 + v) * 32;
            int corner = bucket < 3 ? v : 3 - v;
            vertices.putFloat(at + axis * 4, bucket < 3 ? 16 : 0);
            vertices.putFloat(at + u * 4, x + corners[corner][0]);
            vertices.putFloat(at + w * 4, y + corners[corner][1]);
            vertices.putInt(at + 12, 0xff00ff00 + q);
            vertices.putFloat(at + 16, corners[corner][0] * 0.125f + (distinctMaterials ? q / 512f : 0));
            vertices.putFloat(at + 20, corners[corner][1] * 0.125f);
            vertices.putInt(at + 24, q * 16); // Lighting is allowed to interpolate over a merged patch.
            vertices.put(at + 28 + axis, (byte) (bucket < 3 ? 127 : -127));
        }
        return vertices;
    }

    private static void assertCoverage(ByteBuffer vertices, TerrainLodIndices.Result result, int start, int end, int bucket) {
        int axis = bucket < 3 ? bucket : 6 - bucket, u = (axis + 1) % 3, v = (axis + 2) % 3;
        int[] covered = new int[256];
        for (int i = start; i < end; i += 6) {
            int a = Short.toUnsignedInt(result.indices()[i]), b = Short.toUnsignedInt(result.indices()[i + 1]), c = Short.toUnsignedInt(result.indices()[i + 2]), d = Short.toUnsignedInt(result.indices()[i + 4]);
            assertTrue(a >= 0 && a < 1024 && b >= 0 && b < 1024 && c >= 0 && c < 1024 && d >= 0 && d < 1024);
            float au = vertices.getFloat(a * 32 + u * 4), av = vertices.getFloat(a * 32 + v * 4);
            float bu = vertices.getFloat(b * 32 + u * 4), bv = vertices.getFloat(b * 32 + v * 4);
            float cu = vertices.getFloat(c * 32 + u * 4), cv = vertices.getFloat(c * 32 + v * 4);
            double cross = (bu - au) * (cv - av) - (bv - av) * (cu - au);
            assertTrue(bucket < 3 ? cross > 0 : cross < 0, "Winding changed");
            float du = vertices.getFloat(d * 32 + u * 4), dv = vertices.getFloat(d * 32 + v * 4);
            int minU = (int) Math.min(Math.min(au, bu), Math.min(cu, du));
            int maxU = (int) Math.max(Math.max(au, bu), Math.max(cu, du));
            int minV = (int) Math.min(Math.min(av, bv), Math.min(cv, dv));
            int maxV = (int) Math.max(Math.max(av, bv), Math.max(cv, dv));
            for (int y = minV; y < maxV; y++) for (int x = minU; x < maxU; x++) covered[y * 16 + x]++;
        }
        assertTrue(Arrays.stream(covered).allMatch(n -> n == 1), "Plane contains holes or overlaps");
    }
}
