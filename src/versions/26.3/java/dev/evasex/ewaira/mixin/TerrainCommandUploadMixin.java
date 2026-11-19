package dev.evasex.ewaira.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import dev.evasex.ewaira.terrain.TerrainDrawCommands;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.renderer.DynamicGpuDataStorageMapped;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DynamicGpuDataStorageMapped.class)
abstract class TerrainCommandUploadMixin {
    @Shadow @Final private int blockSize;

    @WrapOperation(method = "writeDataBatchedMultiple", at = @At(value = "INVOKE",
            target = "Ljava/util/List;iterator()Ljava/util/Iterator;", ordinal = 1))
    private Iterator<?> ewaira$copyCommands(List<?> batch, Operation<Iterator<?>> vanilla,
            @Local(index = 7) ByteBuffer mapped, @Local(index = 5) LocalIntRef written) {
        if (batch instanceof TerrainDrawCommands commands && blockSize == TerrainDrawCommands.COMMAND_BYTES
                && commands.copyCachedBytes(mapped, written.get() * blockSize)) {
            written.set(written.get() + commands.size());
            return Collections.emptyIterator();
        }
        return vanilla.call(batch);
    }
}
