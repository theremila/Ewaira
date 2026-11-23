package remila.sex.cirrus.culling;

/**
 * Allocation-free conservative 3D Amanatides-Woo DDA occlusion raycaster.
 * Tests up to 9 sample points of a padded AABB (center + 8 corners).
 * Exits immediately as visible on the first unblocked ray; returns occluded only if ALL rays hit solid blocks.
 */
public final class OcclusionRaycaster {
    private static final double PADDING = 0.2;
    private static final int MAX_STEPS = 96;

    @FunctionalInterface
    public interface OpaqueLookup {
        boolean isOpaque(int blockX, int blockY, int blockZ);
    }

    private OcclusionRaycaster() {}

    public static boolean isBoxOccluded(double camX, double camY, double camZ,
                                        double minX, double minY, double minZ,
                                        double maxX, double maxY, double maxZ,
                                        OpaqueLookup lookup) {
        double pMinX = minX - PADDING, pMinY = minY - PADDING, pMinZ = minZ - PADDING;
        double pMaxX = maxX + PADDING, pMaxY = maxY + PADDING, pMaxZ = maxZ + PADDING;

        // If the camera is inside or adjacent to the padded AABB, never cull.
        if (camX >= pMinX && camX <= pMaxX && camY >= pMinY && camY <= pMaxY && camZ >= pMinZ && camZ <= pMaxZ) {
            return false;
        }

        // Center ray first: fastest rejection for visible entities.
        if (!rayBlocked(camX, camY, camZ, (pMinX + pMaxX) * 0.5, (pMinY + pMaxY) * 0.5, (pMinZ + pMaxZ) * 0.5, lookup)) {
            return false;
        }
        // Check all 8 corners of the conservatively padded AABB; any single clear ray makes the object visible.
        if (!rayBlocked(camX, camY, camZ, pMinX, pMinY, pMinZ, lookup)) return false;
        if (!rayBlocked(camX, camY, camZ, pMaxX, pMinY, pMinZ, lookup)) return false;
        if (!rayBlocked(camX, camY, camZ, pMinX, pMaxY, pMinZ, lookup)) return false;
        if (!rayBlocked(camX, camY, camZ, pMaxX, pMaxY, pMinZ, lookup)) return false;
        if (!rayBlocked(camX, camY, camZ, pMinX, pMinY, pMaxZ, lookup)) return false;
        if (!rayBlocked(camX, camY, camZ, pMaxX, pMinY, pMaxZ, lookup)) return false;
        if (!rayBlocked(camX, camY, camZ, pMinX, pMaxY, pMaxZ, lookup)) return false;
        return rayBlocked(camX, camY, camZ, pMaxX, pMaxY, pMaxZ, lookup);
    }

    static boolean rayBlocked(double x0, double y0, double z0, double x1, double y1, double z1, OpaqueLookup lookup) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        if (!Double.isFinite(dx + dy + dz)) return false;

        int x = floor(x0), y = floor(y0), z = floor(z0);
        int endX = floor(x1), endY = floor(y1), endZ = floor(z1);
        if (x == endX && y == endY && z == endZ) return false;

        int stepX = dx > 0 ? 1 : (dx < 0 ? -1 : 0);
        int stepY = dy > 0 ? 1 : (dy < 0 ? -1 : 0);
        int stepZ = dz > 0 ? 1 : (dz < 0 ? -1 : 0);

        double tMaxX = stepX == 0 ? Double.POSITIVE_INFINITY : ((x + (stepX > 0 ? 1.0 : 0.0)) - x0) / dx;
        double tMaxY = stepY == 0 ? Double.POSITIVE_INFINITY : ((y + (stepY > 0 ? 1.0 : 0.0)) - y0) / dy;
        double tMaxZ = stepZ == 0 ? Double.POSITIVE_INFINITY : ((z + (stepZ > 0 ? 1.0 : 0.0)) - z0) / dz;

        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dx);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dy);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dz);

        for (int step = 0; step < MAX_STEPS; step++) {
            double nextT = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
            if (!(nextT < 1.0)) return false;
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY <= tMaxZ) {
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
            if (x == endX && y == endY && z == endZ) return false;
            if (lookup.isOpaque(x, y, z)) return true;
        }
        return false;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
