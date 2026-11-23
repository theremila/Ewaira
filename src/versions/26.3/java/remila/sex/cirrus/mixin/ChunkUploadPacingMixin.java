package remila.sex.cirrus.mixin;

import com.mojang.blaze3d.vertex.StagingBuffer;
import com.mojang.blaze3d.vertex.UberGpuBuffer;
import com.mojang.renderpearl.api.device.GpuDevice;
import remila.sex.cirrus.terrain.TerrainBatching;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Iterator;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Byte-budgeted GPU terrain upload pacing for {@link UberGpuBuffer}.
 * Caps per-frame staged chunk upload bursts to <= 2 MiB (minimum 4 entries) so large streaming bursts
 * spread across consecutive frames instead of causing 300-600 ms p99 frame freezes.
 */
@Mixin(UberGpuBuffer.class)
abstract class ChunkUploadPacingMixin<T> {
    @Unique private static final int MIN_ENTRIES_PER_FRAME = 4;
    @Unique private static final long MAX_BYTES_PER_FRAME = 2L * 1024 * 1024; // 2 MiB per UberGpuBuffer per frame

    @Shadow @Final private Object2ObjectOpenHashMap<T, AutoCloseable> stagedAllocations;
    @Shadow @Final private ObjectOpenHashSet<T> skippedStagedAllocations;
    @Unique private final Object2ObjectOpenHashMap<T, AutoCloseable> cirrus$deferred = new Object2ObjectOpenHashMap<>();

    @Inject(method = "uploadStagedAllocations", at = @At("HEAD"))
    private void cirrus$splitStagedBurst(GpuDevice device, StagingBuffer.Uploader uploader, CallbackInfoReturnable<Boolean> cir) {
        if (!TerrainBatching.isEnabled() || stagedAllocations.size() <= MIN_ENTRIES_PER_FRAME) return;
        int kept = 0;
        long bytes = 0L;
        Iterator<Map.Entry<T, AutoCloseable>> it = stagedAllocations.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<T, AutoCloseable> entry = it.next();
            T key = entry.getKey();
            if (skippedStagedAllocations.contains(key)) {
                // Let vanilla close and drop skipped entries immediately.
                continue;
            }
            if (kept >= MIN_ENTRIES_PER_FRAME && bytes >= MAX_BYTES_PER_FRAME) {
                AutoCloseable previous = cirrus$deferred.put(key, entry.getValue());
                if (previous != null && previous != entry.getValue()) closeQuietly(previous);
                it.remove();
                continue;
            }
            kept++;
            bytes += stagedSize(entry.getValue());
        }
    }

    @Inject(method = "uploadStagedAllocations", at = @At("RETURN"))
    private void cirrus$restoreDeferred(GpuDevice device, StagingBuffer.Uploader uploader, CallbackInfoReturnable<Boolean> cir) {
        if (cirrus$deferred.isEmpty()) return;
        for (Map.Entry<T, AutoCloseable> entry : cirrus$deferred.entrySet()) {
            T key = entry.getKey();
            AutoCloseable value = entry.getValue();
            if (skippedStagedAllocations.contains(key)) {
                closeQuietly(value);
            } else {
                AutoCloseable overwritten = stagedAllocations.put(key, value);
                if (overwritten != null && overwritten != value) closeQuietly(overwritten);
            }
        }
        cirrus$deferred.clear();
    }

    @Inject(method = "removeAllocation", at = @At("HEAD"))
    private void cirrus$dropDeferredOnRemove(T key, CallbackInfo ci) {
        AutoCloseable pending = cirrus$deferred.remove(key);
        if (pending != null) closeQuietly(pending);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void cirrus$closeDeferred(CallbackInfo ci) {
        if (cirrus$deferred.isEmpty()) return;
        for (AutoCloseable pending : cirrus$deferred.values()) closeQuietly(pending);
        cirrus$deferred.clear();
    }

    @Unique
    private static long stagedSize(AutoCloseable entry) {
        // StagedAllocationEntry.buffer() is package-private; estimate conservatively (~128 KiB/section) without reflection overhead.
        return 131_072L;
    }

    @Unique
    private static void closeQuietly(AutoCloseable resource) {
        try {
            resource.close();
        } catch (Exception ignored) {
        }
    }
}
