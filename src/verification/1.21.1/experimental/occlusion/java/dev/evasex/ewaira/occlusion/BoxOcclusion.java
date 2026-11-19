package dev.evasex.ewaira.occlusion;

import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** A positive result proves every ray to the target box crosses a strictly interior block slab. */
public final class BoxOcclusion {
    private static final double MARGIN = 0.0001;
    private BoxOcclusion() {}
    public static boolean hides(Vec3 camera, AABB target, int x, int y, int z) {
        if (!Double.isFinite(camera.x + camera.y + camera.z)
                || !Double.isFinite(target.minX + target.minY + target.minZ + target.maxX + target.maxY + target.maxZ)) return false;
        return slab(camera.x, camera.y, camera.z, target.minX, target.maxX, target.minY, target.maxY,
                        target.minZ, target.maxZ, x, y, z)
                || slab(camera.y, camera.x, camera.z, target.minY, target.maxY, target.minX, target.maxX,
                        target.minZ, target.maxZ, y, x, z)
                || slab(camera.z, camera.x, camera.y, target.minZ, target.maxZ, target.minX, target.maxX,
                        target.minY, target.maxY, z, x, y);
    }
    private static boolean slab(double eye, double u, double v, double min, double max,
            double minU, double maxU, double minV, double maxV, int block, int blockU, int blockV) {
        if (!((eye < block - MARGIN && block + 1 + MARGIN < min)
                || (max < block - MARGIN && block + 1 + MARGIN < eye))) return false;
        for (int depth = 0; depth < 2; depth++) {
            double denominator = (depth == 0 ? min : max) - eye;
            for (int face = 0; face < 2; face++) {
                double t = (block + face - eye) / denominator;
                if (!(u + t * (minU - u) > blockU + MARGIN
                        && u + t * (maxU - u) < blockU + 1 - MARGIN
                        && v + t * (minV - v) > blockV + MARGIN
                        && v + t * (maxV - v) < blockV + 1 - MARGIN)) return false;
            }
        }
        return true;
    }
    @FunctionalInterface
    public interface Occupancy { boolean opaque(int x, int y, int z); }

    public static boolean hidden(Vec3 camera, AABB target, Occupancy occupancy) {
        double dx = (target.minX + target.maxX) * 0.5 - camera.x;
        double dy = (target.minY + target.maxY) * 0.5 - camera.y;
        double dz = (target.minZ + target.maxZ) * 0.5 - camera.z;
        if (!Double.isFinite(dx + dy + dz) || !Double.isFinite(camera.x + camera.y + camera.z)) return false;
        int x = Mth.floor(camera.x), y = Mth.floor(camera.y), z = Mth.floor(camera.z);
        int sx = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int sy = dy > 0 ? 1 : dy < 0 ? -1 : 0;
        int sz = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double tx = boundary(camera.x, x, dx, sx), ty = boundary(camera.y, y, dy, sy), tz = boundary(camera.z, z, dz, sz);
        double stepx = 1 / Math.abs(dx), stepy = 1 / Math.abs(dy), stepz = 1 / Math.abs(dz);
        for (int i = 0; i < 8; i++) {
            double next = Math.min(tx, Math.min(ty, tz));
            if (!(next < 1)) return false;
            if (tx <= ty && tx <= tz) { x += sx; tx += stepx; }
            else if (ty <= tz) { y += sy; ty += stepy; }
            else { z += sz; tz += stepz; }
            if (!BoxOcclusion.hides(camera, target, x, y, z)) continue;
            if (occupancy.opaque(x, y, z)) return true;
        }
        return false;
    }
    private static double boundary(double eye, int cell, double delta, int step) {
        return step == 0 ? Double.POSITIVE_INFINITY : (cell + (step > 0 ? 1.0 : 0.0) - eye) / delta;
    }
}
