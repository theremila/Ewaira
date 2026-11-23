package remila.sex.cirrus.terrain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainFacesTest {
    private static final int STRIDE = 28;

    @Test void cubeOutsideSendsHalfItsQuadsWithoutExtraDraws() {
        ByteBuffer vertices = cube();
        TerrainFaces faces = TerrainFaces.sort(vertices, STRIDE, 24);
        assertNotNull(faces);
        long range = faces.range(36, 10, 10, 10);
        assertEquals(0, TerrainFaces.firstIndex(range));
        assertEquals(18, TerrainFaces.indexCount(range));
        assertEquals(18, TerrainFaces.indexCount(faces.range(36, -10, -10, -10)));
        assertEquals(0, TerrainFaces.indexCount(faces.range(36, 0.5, 0.5, 0.5)));
        assertEquals(18, TerrainFaces.indexCount(faces.range(36, 0, 0, 0)));

        // Exact multi-bucket ranges skip hidden interior buckets completely even on mixed-sign corners (+X, +Y, -Z).
        long[] ranges = new long[TerrainFaces.MAX_RANGES];
        int count = faces.ranges(36, 10, 10, -10, true, ranges);
        assertEquals(2, count);
        int totalIndices = 0;
        for (int i = 0; i < count; i++) totalIndices += TerrainFaces.indexCount(ranges[i]);
        assertEquals(18, totalIndices); // Exactly 3 visible faces, whereas enclosing range sent 4 faces (24 indices).
        assertEquals(24, TerrainFaces.indexCount(faces.range(36, 10, 10, -10)));
    }

    @Test void nativeAttributesAndCoplanarOrderSurviveStableGrouping() {
        ByteBuffer vertices = ByteBuffer.allocate(24 * STRIDE).order(ByteOrder.nativeOrder());
        for (int q = 0; q < 6; q++) quad(vertices, q, q % 2 == 0 ? 6 : 0, 0, q + 1);
        byte[][] original = new byte[6][];
        for (int q = 0; q < 6; q++) original[q] = Arrays.copyOfRange(vertices.array(), q * 4 * STRIDE, (q + 1) * 4 * STRIDE);
        assertNotNull(TerrainFaces.sort(vertices, STRIDE, 24));
        int[] order = {1, 3, 5, 0, 2, 4};
        for (int q = 0; q < 6; q++) assertArrayEquals(original[order[q]],
                Arrays.copyOfRange(vertices.array(), q * 4 * STRIDE, (q + 1) * 4 * STRIDE));
    }

    @Test void randomCamerasKeepEveryFrontFacingTriangleAndStayInsideNativeIndices() {
        Random random = new Random(4031);
        int quads = 251;
        ByteBuffer vertices = ByteBuffer.allocate(quads * 4 * STRIDE).order(ByteOrder.nativeOrder());
        for (int q = 0; q < quads; q++) quad(vertices, q, new int[]{0, 1, 2, 4, 5, 6}[random.nextInt(6)], random.nextFloat(-1, 17), q);
        TerrainFaces faces = TerrainFaces.sort(vertices, STRIDE, quads * 4);
        assertNotNull(faces);
        for (int test = 0; test < 1000; test++) {
            double[] eye = {random.nextDouble(-1000, 1000), random.nextDouble(-1000, 1000), random.nextDouble(-1000, 1000)};
            long range = faces.range(quads * 6, eye[0], eye[1], eye[2]);
            int first = TerrainFaces.firstIndex(range) / 6, end = first + TerrainFaces.indexCount(range) / 6;
            assertTrue(first >= 0 && first <= end && end <= quads);
            for (int q = 0; q < quads; q++) {
                int at = q * 4 * STRIDE;
                double[] p = point(vertices, at), a = point(vertices, at + STRIDE), b = point(vertices, at + STRIDE * 2);
                double nx = (a[1]-p[1])*(b[2]-p[2])-(a[2]-p[2])*(b[1]-p[1]);
                double ny = (a[2]-p[2])*(b[0]-p[0])-(a[0]-p[0])*(b[2]-p[2]);
                double nz = (a[0]-p[0])*(b[1]-p[1])-(a[1]-p[1])*(b[0]-p[0]);
                if (nx*(eye[0]-p[0])+ny*(eye[1]-p[1])+nz*(eye[2]-p[2]) > 0) {
                    assertTrue(q >= first && q < end, "Front-facing triangle was removed");
                }
            }
        }
    }

    @Test void badLayoutsAndUnsupportedGeometryLeaveTheBufferUntouched() {
        ByteBuffer vertices = cube();
        byte[] before = vertices.array().clone();
        assertNull(TerrainFaces.sort(vertices.asReadOnlyBuffer(), STRIDE, 24));
        assertNull(TerrainFaces.sort(vertices, 8, 24));
        assertNull(TerrainFaces.sort(vertices, STRIDE, 23));
        assertNull(TerrainFaces.sort(vertices, STRIDE, 28));
        assertArrayEquals(before, vertices.array());
        // Both triangles lie in one plane but wind in opposite directions.
        ByteBuffer folded = ByteBuffer.allocate(4 * STRIDE).order(ByteOrder.nativeOrder());
        float[][] points = {{0,0,0},{0,1,0},{0,0,1},{0,1,1}};
        for (int v = 0; v < 4; v++) for (int a = 0; a < 3; a++) folded.putFloat(v*STRIDE+a*4, points[v][a]);
        byte[] foldedBefore = folded.array().clone();
        assertNull(TerrainFaces.sort(folded, STRIDE, 4));
        assertArrayEquals(foldedBefore, folded.array());
    }

    @Test void slopedGeometryIsAlwaysIncluded() {
        ByteBuffer vertices = ByteBuffer.allocate(8 * STRIDE).order(ByteOrder.nativeOrder());
        quad(vertices, 0, 0, 1, 0);
        quad(vertices, 1, 0, 1, 1);
        for (int v = 0; v < 4; v++) vertices.putFloat(4*STRIDE+v*STRIDE, vertices.getFloat(4*STRIDE+v*STRIDE+4));
        TerrainFaces faces = TerrainFaces.sort(vertices, STRIDE, 8);
        assertNotNull(faces);
        long range = faces.range(12, -100, -100, -100);
        assertEquals(6, TerrainFaces.firstIndex(range));
        assertEquals(6, TerrainFaces.indexCount(range));
    }

    @Test void marginAndNonfiniteCameraKeepConservativeRanges() {
        TerrainFaces faces = TerrainFaces.sort(cube(), STRIDE, 24);
        assertNotNull(faces);
        assertEquals(36, TerrainFaces.indexCount(faces.range(36, Double.NaN, 0, 0)));
        assertEquals(12, TerrainFaces.indexCount(faces.range(12, 100, 100, 100)));
        assertEquals(6, TerrainFaces.indexCount(faces.range(36, 0.5, 1.0 / 32, 0.5)));
    }

    @Test void slicedDirectBufferDoesNotTouchNeighborsOrPositions() {
        ByteBuffer arena = ByteBuffer.allocateDirect(32 + 24 * STRIDE).order(ByteOrder.nativeOrder());
        arena.putInt(0, 0x13572468); arena.putInt(arena.capacity()-4, 0x24681357);
        arena.position(16); arena.limit(arena.capacity()-16);
        ByteBuffer slice = arena.slice().order(ByteOrder.nativeOrder()); slice.put(0, cube().array());
        assertNotNull(TerrainFaces.sort(arena, STRIDE, 24));
        assertEquals(16, arena.position()); assertEquals(arena.capacity()-16, arena.limit());
        assertEquals(0x13572468, arena.getInt(0));
        arena.limit(arena.capacity()); assertEquals(0x24681357, arena.getInt(arena.capacity()-4));
    }

    @Test void configurableMarginOnlyKeepsMoreGeometry() {
        TerrainFaces faces = TerrainFaces.sort(cube(), STRIDE, 24);
        assertNotNull(faces);
        try {
            TerrainOptimization.setMargin(1.0 / 16);
            int original = TerrainFaces.indexCount(faces.range(36, 0.5, 0.1, 0.5));
            TerrainOptimization.setMargin(0.25);
            int conservative = TerrainFaces.indexCount(faces.range(36, 0.5, 0.1, 0.5));
            assertEquals(0, original);
            assertEquals(6, conservative);
        } finally { TerrainOptimization.setMargin(1.0 / 16); }
    }

    @Test void everyDirectionSubsetMatchesTriangleVisibilityAtMarginBoundaries() {
        Random random = new Random(5421);
        int[] directions = {0, 1, 2, 4, 5, 6};
        for (int mask = 1; mask < 64; mask++) {
            int quads = Integer.bitCount(mask);
            ByteBuffer vertices = ByteBuffer.allocate(quads * 4 * STRIDE).order(ByteOrder.nativeOrder());
            for (int bit = 0, q = 0; bit < 6; bit++) if ((mask & (1 << bit)) != 0) {
                int bucket = directions[bit];
                quad(vertices, q++, bucket, bucket < 3 ? 1 : 0, bit);
            }
            TerrainFaces faces = TerrainFaces.sort(vertices, STRIDE, quads * 4);
            for (int sample = 0; sample < 100; sample++) {
                double[] eye = {random.nextDouble(-2, 2), random.nextDouble(-2, 2), random.nextDouble(-2, 2)};
                if (sample < 3) eye[sample] = 1 - TerrainOptimization.margin();
                int first = -1, last = 0;
                for (int q = 0; q < quads; q++) {
                    int at = q * 4 * STRIDE;
                    double[] p = point(vertices, at), a = point(vertices, at + STRIDE), b = point(vertices, at + STRIDE * 2);
                    double nx = (a[1]-p[1])*(b[2]-p[2])-(a[2]-p[2])*(b[1]-p[1]);
                    double ny = (a[2]-p[2])*(b[0]-p[0])-(a[0]-p[0])*(b[2]-p[2]);
                    double nz = (a[0]-p[0])*(b[1]-p[1])-(a[1]-p[1])*(b[0]-p[0]);
                    if (nx*(eye[0]-p[0])+ny*(eye[1]-p[1])+nz*(eye[2]-p[2]) + TerrainOptimization.margin() > 0) {
                        if (first < 0) first = q;
                        last = q + 1;
                    }
                }
                long range = faces.range(quads * 6, eye[0], eye[1], eye[2]);
                assertEquals(first < 0 ? 0 : first * 6, TerrainFaces.firstIndex(range));
                assertEquals(first < 0 ? 0 : (last - first) * 6, TerrainFaces.indexCount(range));
            }
        }
    }

    @Test void repeatedConcurrentSortsPreserveNativeBytesAcrossScratchGrowth() throws Exception {
        try (var workers = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<Callable<Void>>();
            for (int task = 0; task < 24; task++) {
                int marker = task;
                tasks.add(() -> {
                    for (int count : new int[]{12, 600, 6, 1200, 6}) {
                        ByteBuffer vertices = ByteBuffer.allocate(count * 4 * STRIDE).order(ByteOrder.nativeOrder());
                        for (int q = 0; q < count; q++) quad(vertices, q, q % 2 == 0 ? 6 : 0, 0, marker * 10000 + q);
                        byte[] original = vertices.array().clone();
                        assertNotNull(TerrainFaces.sort(vertices, STRIDE, count * 4));
                        for (int q = 0; q < count; q++) {
                            int source = q < count / 2 ? q * 2 + 1 : (q - count / 2) * 2;
                            assertArrayEquals(Arrays.copyOfRange(original, source * 4 * STRIDE, (source + 1) * 4 * STRIDE),
                                    Arrays.copyOfRange(vertices.array(), q * 4 * STRIDE, (q + 1) * 4 * STRIDE));
                        }
                        byte[] ordered = vertices.array().clone();
                        assertNotNull(TerrainFaces.sort(vertices, STRIDE, count * 4));
                        assertArrayEquals(ordered, vertices.array());
                    }
                    return null;
                });
            }
            for (var result : workers.invokeAll(tasks)) result.get();
        }
    }

    private static ByteBuffer cube() {
        ByteBuffer vertices = ByteBuffer.allocate(24 * STRIDE).order(ByteOrder.nativeOrder());
        int[] directions = {6, 1, 4, 0, 5, 2};
        for (int q = 0; q < 6; q++) quad(vertices, q, directions[q], directions[q] < 3 ? 1 : 0, q);
        return vertices;
    }
    private static double[] point(ByteBuffer b, int at) { return new double[]{b.getFloat(at), b.getFloat(at+4), b.getFloat(at+8)}; }
    private static void quad(ByteBuffer buffer, int q, int bucket, float plane, int marker) {
        int axis = bucket < 3 ? bucket : 6-bucket;
        int a = (axis+1)%3, b = (axis+2)%3;
        float[][] uv = {{0,0},{1,0},{1,1},{0,1}};
        for (int v = 0; v < 4; v++) {
            int at = (q*4+v)*STRIDE;
            buffer.putFloat(at+axis*4, plane);
            int corner = bucket < 3 ? v : 3-v;
            buffer.putFloat(at+a*4, uv[corner][0]); buffer.putFloat(at+b*4, uv[corner][1]);
            for (int offset=12; offset<STRIDE; offset+=4) buffer.putInt(at+offset, marker*100+offset+v);
        }
    }
}
