package remila.sex.cirrus.lod;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import remila.sex.cirrus.ModIdentity;
import remila.sex.cirrus.debug.RenderDiagnostics;
import remila.sex.cirrus.debug.RenderDiagnostics.Metric;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Regional meshes in the opaque pass. The render thread owns all GPU buffers. */
public final class FarTerrainRenderer implements AutoCloseable {
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath(ModIdentity.MOD_ID, "core/far_terrain");
    public static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(ModIdentity.MOD_ID, "pipeline/far_terrain"))
            .withVertexShader(SHADER).withFragmentShader(SHADER)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withCull(true)
            .build();

    private static final int CHUNK = FarTerrainLod.CHUNK_SIZE;
    private static final int REGION = FarTerrainCache.REGION_CHUNKS;
    private static final int MAX_GPU_BYTES = 48 * 1024 * 1024;
    private static final long IDLE_SCAN_MS = 50;
    private static final long REBUILD_DEBOUNCE_MS = 750;
    private static final int REBUILD_BATCH_TILES = 32;
    private static final long MIN_REBUILD_INTERVAL_MS = 25;
    private record Detail(long revision, int distance, int focal) {}
    private record Job(FarTerrainCache.Region region, Detail detail, AABB bounds,
                       CompletableFuture<FarTerrainMesh> mesh) {}
    private record Resident(FarTerrainCache.Region region, Detail detail, AABB bounds,
                            GpuBufferSlice vertices, int count, int bytes) {}

    private final Long2ObjectOpenHashMap<Resident> residents = new Long2ObjectOpenHashMap<>();
    private final Matrix4f rotation = new Matrix4f();
    private final Matrix4f texture = new Matrix4f();
    private final Vector4f limits = new Vector4f();
    private final Vector3f offset = new Vector3f();
    private FarTerrainCache cache;
    private Job pending;
    private int inner, outer, residentBytes;
    private long nextScan;
    private long lastRebuildMs;
    private boolean coolDown;
    private int consecutiveDeferrals;

    public void prepare(Minecraft client, CameraRenderState camera) {
        if (!FarTerrainLod.isEnabled() || !(client.level instanceof FarTerrainAccess access)) {
            close();
            return;
        }
        if (cache != access.cirrus$farTerrain()) {
            close();
            cache = access.cirrus$farTerrain();
            attachWorldDiskCache(client);
        }
        if (visible(camera)) {
            cache.prefetchAround((int) Math.floor(camera.pos.x) >> 4, (int) Math.floor(camera.pos.z) >> 4,
                    FarTerrainLod.MAX_DISTANCE_CHUNKS);
        }
        if (!visible(camera) || cache.regions().isEmpty() || RenderSystem.getCompiledPipelineNullable(PIPELINE) == null) return;
        inner = (client.options.getEffectiveRenderDistance() + 1) * CHUNK;
        outer = Math.min(FarTerrainLod.MAX_DISTANCE_CHUNKS, (int) (camera.depthFar / CHUNK) - 1) * CHUNK;
        if (outer <= inner) return;

        if (coolDown) {
            coolDown = false;
            return;
        }
        if (pending != null) return;
        long now = Util.getMillis();
        if (now < nextScan) return;
        int focal = FarTerrainLod.focalPixels(client.getWindow().getWidth(), client.getWindow().getHeight(), camera.projectionMatrix.m11());
        if (focal == 0) return;
        focal = 16 * ((focal + 15) / 16);
        prune(camera);
        if (!schedule(camera, focal)) nextScan = now + IDLE_SCAN_MS;
    }

    private boolean schedule(CameraRenderState camera, int focal) {
        FarTerrainCache.Region candidate = null;
        double closest = Double.POSITIVE_INFINITY;
        int detailDistance = 0;
        long nowMs = System.currentTimeMillis();
        boolean canRebuildResident = nowMs - lastRebuildMs >= MIN_REBUILD_INTERVAL_MS;
        for (var region : cache.regions()) {
            double distance = distanceSquared(camera, region);
            if (distance >= closest || !inRing(camera, region, distance)) continue;
            int band = 128 * ((int) Math.sqrt(distance) / 128);
            Resident resident = residents.get(region.key);
            if (resident != null && resident.region() == region) {
                boolean sameDetail = resident.detail().focal() == focal && resident.detail().distance() == band;
                if (resident.detail().revision() == region.revision && sameDetail) continue;
                // Pace resident rebuilds and debounce incremental tile updates to prevent GPU buffer churn while flying.
                if (!canRebuildResident || (sameDetail
                        && region.dirtyTiles < REBUILD_BATCH_TILES && nowMs - region.lastDirtyMs < REBUILD_DEBOUNCE_MS)) {
                    continue;
                }
            }
            if (!camera.cullFrustum.isVisible(region.bounds())) continue;
            closest = distance;
            detailDistance = band;
            candidate = region;
        }
        if (candidate == null) return false;
        var region = candidate;
        region.dirtyTiles = 0;
        if (residents.containsKey(region.key)) lastRebuildMs = nowMs;
        var detail = new Detail(region.revision, detailDistance, focal);
        var tiles = cache.snapshot(region);
        boolean debug = RenderDiagnostics.enabled();
        pending = new Job(region, detail, region.bounds(), CompletableFuture.supplyAsync(() -> {
            long started = debug ? System.nanoTime() : 0;
            FarTerrainMesh mesh = FarTerrainMesh.build(tiles, region.x, region.z, detail.distance(), detail.focal());
            if (debug) {
                RenderDiagnostics.add(Metric.FAR_BUILDS, 1);
                RenderDiagnostics.add(Metric.FAR_BUILD_US, (System.nanoTime() - started) / 1000);
            }
            return mesh;
        }, Util.backgroundExecutor()));
        return true;
    }

    public boolean hasUpload() { return pending != null && pending.mesh().isDone(); }

    public void uploadAfterDetail(CameraRenderState camera, long detailUploadNanos) {
        if (!hasUpload() || !visible(camera) || outer <= inner) return;
        if (detailUploadNanos > 1_500_000 && consecutiveDeferrals < 8) {
            consecutiveDeferrals++;
            if (RenderDiagnostics.enabled()) RenderDiagnostics.add(Metric.FAR_UPLOAD_DEFERRALS, 1);
            return;
        }
        consecutiveDeferrals = 0;
        Job job = pending;
        pending = null;
        var region = job.region();
        try (FarTerrainMesh result = job.mesh().join()) {
            if (result == null || cache.region(region.key) != region
                    || job.detail().revision() != region.revision
                    || !inRing(camera, region, distanceSquared(camera, region))
                    || !camera.cullFrustum.isVisible(job.bounds())) return;

            int bytes = result.mesh().vertexBuffer().remaining();
            Resident previous = residents.get(region.key);
            if (!makeRoom(camera, region, bytes - (previous == null ? 0 : previous.bytes()))) {
                nextScan = Util.getMillis() + IDLE_SCAN_MS;
                return;
            }
            long started = System.nanoTime();
            GpuBuffer uploaded = RenderSystem.getDevice().createBuffer(() -> ModIdentity.MOD_NAME + " distant terrain",
                    GpuBuffer.USAGE_VERTEX, result.mesh().vertexBuffer());
            residents.put(region.key, new Resident(region, job.detail(), job.bounds(), uploaded.slice(),
                    result.mesh().drawState().vertexCount(), bytes));
            residentBytes += bytes;
            if (previous != null) release(previous);
            long duration = System.nanoTime() - started;
            coolDown = duration > 2_000_000;
            if (RenderDiagnostics.enabled()) {
                RenderDiagnostics.add(Metric.FAR_UPLOAD_BYTES, bytes);
                RenderDiagnostics.add(Metric.FAR_UPLOAD_US, duration / 1000);
            }
        } catch (CompletionException failure) {
            ModIdentity.LOGGER.warn("Could not build distant terrain", failure.getCause());
            nextScan = Util.getMillis() + IDLE_SCAN_MS;
        }
    }

    private boolean makeRoom(CameraRenderState camera, FarTerrainCache.Region incoming, int needed) {
        double incomingDistance = distanceSquared(camera, incoming);
        while (residentBytes + needed > MAX_GPU_BYTES) {
            Resident farthest = null;
            double distance = incomingDistance;
            for (Resident resident : residents.values()) {
                double candidateDistance = distanceSquared(camera, resident.region());
                if (candidateDistance > distance) {
                    distance = candidateDistance;
                    farthest = resident;
                }
            }
            if (farthest == null) return false;
            residents.remove(farthest.region().key);
            release(farthest);
        }
        return true;
    }

    private void prune(CameraRenderState camera) {
        var iterator = residents.values().iterator();
        double keepRadius = outer + REGION * CHUNK;
        while (iterator.hasNext()) {
            Resident resident = iterator.next();
            if (cache.region(resident.region().key) != resident.region()
                    || distanceSquared(camera, resident.region()) > keepRadius * keepRadius) {
                iterator.remove();
                release(resident);
            }
        }
    }

    public void draw(RenderPass pass, CameraRenderState camera) {
        if (residents.isEmpty() || outer <= inner || !visible(camera)) return;
        var pipeline = RenderSystem.getCompiledPipelineNullable(PIPELINE);
        if (pipeline == null) return;
        rotation.set(RenderSystem.getModelViewStack());
        limits.set(inner, outer, 0.0f, 1.0f);

        int draws = 0, vertices = 0;
        for (Resident resident : residents.values()) {
            var region = resident.region();
            if (cache.region(region.key) != region || !inRing(camera, region, distanceSquared(camera, region))
                    || !camera.cullFrustum.isVisible(resident.bounds())) continue;
            if (draws == 0) pass.setPipeline(pipeline);
            offset.set((float) (region.x * CHUNK - camera.pos.x), (float) -camera.pos.y,
                    (float) (region.z * CHUNK - camera.pos.z));
            pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(rotation, limits, offset, texture));
            pass.setVertexBuffer(0, resident.vertices());
            pass.draw(resident.count(), 1, 0, 0);
            draws++;
            vertices += resident.count();
        }
        if (draws > 0 && RenderDiagnostics.enabled()) {
            RenderDiagnostics.observeFarRadius(outer / CHUNK);
            RenderDiagnostics.add(Metric.FAR_DRAWS, draws);
            RenderDiagnostics.add(Metric.FAR_VERTICES, vertices);
        }
    }

    private boolean inRing(CameraRenderState camera, FarTerrainCache.Region region, double distance) {
        return distance < (double) outer * outer
                && (Math.abs((region.x + REGION / 2) * CHUNK - camera.pos.x) + REGION * CHUNK / 2 > inner
                || Math.abs((region.z + REGION / 2) * CHUNK - camera.pos.z) + REGION * CHUNK / 2 > inner);
    }

    private static double distanceSquared(CameraRenderState camera, FarTerrainCache.Region region) {
        double dx = Math.max(0, Math.abs((region.x + REGION / 2) * CHUNK - camera.pos.x) - REGION * CHUNK / 2);
        double dz = Math.max(0, Math.abs((region.z + REGION / 2) * CHUNK - camera.pos.z) - REGION * CHUNK / 2);
        return dx * dx + dz * dz;
    }

    private static boolean visible(CameraRenderState camera) {
        return camera != null && camera.pos != null && camera.cullFrustum != null
                && (camera.fogType == FogType.ATMOSPHERIC || camera.fogType == FogType.NONE);
    }

    private void release(Resident resident) {
        resident.vertices().buffer().close();
        residentBytes -= resident.bytes();
    }

    private static void attachWorldDiskCache(Minecraft client) {
        if (client.level == null || !(client.level instanceof FarTerrainAccess access)) return;
        String worldKey;
        if (client.hasSingleplayerServer() && client.getSingleplayerServer() != null) {
            worldKey = "sp_" + sanitize(client.getSingleplayerServer().getWorldData().getLevelName());
        } else if (client.getCurrentServer() != null) {
            worldKey = "mp_" + sanitize(client.getCurrentServer().ip);
        } else {
            return;
        }
        String dim = sanitize(client.level.dimension().identifier().toString());
        access.cirrus$farTerrain().attachDiskCache(client.gameDirectory.toPath().resolve("cirrus-lod").resolve(worldKey).resolve(dim));
    }

    private static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) return "default";
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    @Override
    public void close() {
        if (cache != null) cache.flushDirtyToDisk();
        if (pending != null) {
            pending.mesh().thenAccept(result -> {
                if (result != null) result.close();
            });
            pending = null;
        }
        for (Resident resident : residents.values()) release(resident);
        residents.clear();
        cache = null;
        nextScan = 0;
        inner = outer = 0;
        coolDown = false;
        consecutiveDeferrals = 0;
    }
}
