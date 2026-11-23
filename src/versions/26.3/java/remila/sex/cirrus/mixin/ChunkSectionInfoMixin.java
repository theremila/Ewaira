package remila.sex.cirrus.mixin;

import java.nio.ByteBuffer;
import net.minecraft.client.renderer.DynamicGpuData.ChunkSectionInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Eliminates per-section {@code Std140Builder} heap allocations on both mapped and non-mapped
 * GPU chunk-section uploads (including {@code Chunk Sections Instanced}).
 */
@Mixin(ChunkSectionInfo.class)
abstract class ChunkSectionInfoMixin {
    @Shadow @Final private int x;
    @Shadow @Final private int y;
    @Shadow @Final private int z;
    @Shadow @Final private float visibility;

    @Inject(method = "write", at = @At("HEAD"), cancellable = true)
    private void cirrus$writeWithoutStd140Builder(ByteBuffer buffer, CallbackInfo ci) {
        if ((buffer.position() & 15) == 0) {
            buffer.putInt(x).putInt(y).putInt(z).putFloat(visibility);
            ci.cancel();
        }
    }
}
