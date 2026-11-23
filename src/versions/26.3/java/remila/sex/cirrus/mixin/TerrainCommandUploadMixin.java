package remila.sex.cirrus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import remila.sex.cirrus.terrain.TerrainDrawCommands;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.renderer.DynamicGpuData.ChunkSectionInfo;
import net.minecraft.client.renderer.DynamicGpuDataStorageMapped;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DynamicGpuDataStorageMapped.class)
abstract class TerrainCommandUploadMixin {
    @Shadow @Final private int blockSize;

    @WrapOperation(method = "writeDataBatchedMultiple", at = @At(value = "INVOKE",
            target = "Ljava/util/List;iterator()Ljava/util/Iterator;", ordinal = 1))
    private Iterator<?> cirrus$copyCommands(List<?> batch, Operation<Iterator<?>> vanilla,
            @Local(index = 7) ByteBuffer mapped, @Local(index = 5) LocalIntRef written) {
        if (batch instanceof TerrainDrawCommands commands && blockSize == TerrainDrawCommands.COMMAND_BYTES
                && commands.copyCachedBytes(mapped, written.get() * blockSize)) {
            written.set(written.get() + commands.size());
            return Collections.emptyIterator();
        }
        return vanilla.call(batch);
    }

    /** Direct native write of 16-byte ChunkSectionInfo structs, avoiding per-section Std140Builder heap allocations. */
    @WrapOperation(method = "writeDataBatched", at = @At(value = "INVOKE",
            target = "Ljava/util/List;size()I", ordinal = 3))
    private int cirrus$bulkWriteSectionInfos(List<?> batch, Operation<Integer> vanilla,
            @Local(index = 5) ByteBuffer mapped, @Local(index = 6) LocalIntRef index) {
        int count = vanilla.call(batch);
        if (index.get() == 0 && count > 0 && blockSize == 16 && mapped.isDirect()
                && mapped.order() == ByteOrder.nativeOrder()
                && batch.get(0) instanceof ChunkSectionInfo) {
            long base = MemoryUtil.memAddress0(mapped);
            for (int i = 0; i < count; i++) {
                ChunkSectionInfo info = (ChunkSectionInfo) batch.get(i);
                long ptr = base + ((long) i << 4);
                MemoryUtil.memPutInt(ptr, info.x());
                MemoryUtil.memPutInt(ptr + 4, info.y());
                MemoryUtil.memPutInt(ptr + 8, info.z());
                MemoryUtil.memPutFloat(ptr + 12, info.visibility());
            }
            mapped.position(count << 4);
            return 0;
        }
        return count;
    }
}
