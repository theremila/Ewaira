package remila.sex.cirrus.culling;

import remila.sex.cirrus.ModIdentity;
import remila.sex.cirrus.debug.RenderDiagnostics;
import remila.sex.cirrus.debug.RenderDiagnostics.Metric;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * Zero-allocation conservative occlusion culling for entities and block entities using compiled section occupancy masks.
 * Uses a flat primitive direct-mapped cache with short TTL and instant invalidation on camera movement or block edits.
 */
public final class EntityOcclusionEngine {
    private static final int CACHE_SIZE = 8192;
    private static final int CACHE_MASK = CACHE_SIZE - 1;
    private static final long TTL_NANOS = 30_000_000L; // 30 ms
    private static final double MIN_CULL_DIST_SQ = 6.0 * 6.0;
    private static final double MAX_CULL_DIST_SQ = 96.0 * 96.0;
    private static final double CAMERA_MOVE_INVALIDATE_SQ = 0.25 * 0.25;
    private static final double MAX_ENTITY_EXTENT = 4.0;

    private static volatile boolean enabled = !ModIdentity.disabledBySystemProperty();

    private static final long[] cacheKeys = new long[CACHE_SIZE];
    private static final long[] cacheTimestamps = new long[CACHE_SIZE];
    private static final int[] cacheGenerations = new int[CACHE_SIZE];
    private static final byte[] cacheHidden = new byte[CACHE_SIZE];

    private static int cacheEpoch = 1;
    private static long lastOccupancyGen = -1L;
    private static double lastCamX = Double.NaN, lastCamY = Double.NaN, lastCamZ = Double.NaN;

    private EntityOcclusionEngine() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }

    public static void invalidateCache() {
        cacheEpoch++;
        if (cacheEpoch == 0) cacheEpoch = 1;
    }

    public static boolean isEntityOccluded(Entity entity, double camX, double camY, double camZ) {
        if (!enabled || entity == null || SectionOccupancyMap.size() == 0) return false;
        if (entity.isCurrentlyGlowing() || entity.hasCustomName() || entity.isVehicle() || entity.isPassenger()) return false;
        if (entity instanceof Leashable leashable && leashable.isLeashed()) return false;

        AABB box = entity.getBoundingBox();
        if (box.getXsize() > MAX_ENTITY_EXTENT || box.getYsize() > MAX_ENTITY_EXTENT || box.getZsize() > MAX_ENTITY_EXTENT) {
            return false;
        }
        double cx = (box.minX + box.maxX) * 0.5 - camX;
        double cy = (box.minY + box.maxY) * 0.5 - camY;
        double cz = (box.minZ + box.maxZ) * 0.5 - camZ;
        double distSq = cx * cx + cy * cy + cz * cz;
        if (distSq < MIN_CULL_DIST_SQ || distSq > MAX_CULL_DIST_SQ) return false;

        long key = ((long) entity.getId() << 1) | 1L;
        return checkCached(key, camX, camY, camZ, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    public static boolean isBlockEntityOccluded(BlockEntity entity, double camX, double camY, double camZ) {
        if (!enabled || entity == null || SectionOccupancyMap.size() == 0) return false;
        if (entity instanceof BeaconBlockEntity || entity instanceof TheEndGatewayBlockEntity
                || entity instanceof StructureBlockEntity) return false;

        BlockPos pos = entity.getBlockPos();
        double minX = pos.getX() - 0.25, minY = pos.getY() - 0.25, minZ = pos.getZ() - 0.25;
        double maxX = pos.getX() + 1.25, maxY = pos.getY() + 1.25, maxZ = pos.getZ() + 1.25;
        double cx = pos.getX() + 0.5 - camX;
        double cy = pos.getY() + 0.5 - camY;
        double cz = pos.getZ() + 0.5 - camZ;
        double distSq = cx * cx + cy * cy + cz * cz;
        if (distSq < MIN_CULL_DIST_SQ || distSq > MAX_CULL_DIST_SQ) return false;

        long key = (pos.asLong() << 1);
        return checkCached(key, camX, camY, camZ, minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean checkCached(long key, double camX, double camY, double camZ,
                                       double minX, double minY, double minZ,
                                       double maxX, double maxY, double maxZ) {
        long occGen = SectionOccupancyMap.generation();
        double dcx = camX - lastCamX, dcy = camY - lastCamY, dcz = camZ - lastCamZ;
        if (occGen != lastOccupancyGen || Double.isNaN(lastCamX)
                || dcx * dcx + dcy * dcy + dcz * dcz > CAMERA_MOVE_INVALIDATE_SQ) {
            lastOccupancyGen = occGen;
            lastCamX = camX;
            lastCamY = camY;
            lastCamZ = camZ;
            invalidateCache();
        }
        int slot = mix(key) & CACHE_MASK;
        long now = System.nanoTime();
        if (cacheGenerations[slot] == cacheEpoch && cacheKeys[slot] == key && (now - cacheTimestamps[slot]) < TTL_NANOS) {
            boolean hidden = cacheHidden[slot] != 0;
            if (hidden && RenderDiagnostics.enabled()) RenderDiagnostics.add(Metric.OCCLUDED_ENTITIES, 1);
            return hidden;
        }
        boolean hidden = OcclusionRaycaster.isBoxOccluded(camX, camY, camZ, minX, minY, minZ, maxX, maxY, maxZ,
                SectionOccupancyMap::isOpaque);
        cacheKeys[slot] = key;
        cacheTimestamps[slot] = now;
        cacheGenerations[slot] = cacheEpoch;
        cacheHidden[slot] = (byte) (hidden ? 1 : 0);
        if (hidden && RenderDiagnostics.enabled()) RenderDiagnostics.add(Metric.OCCLUDED_ENTITIES, 1);
        return hidden;
    }

    private static int mix(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        h ^= h >>> 32;
        return (int) (h ^ (h >>> 16));
    }
}
