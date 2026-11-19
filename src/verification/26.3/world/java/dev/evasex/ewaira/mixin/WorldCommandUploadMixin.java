package dev.evasex.ewaira.mixin;

import dev.evasex.ewaira.terrain.TerrainDrawCommands;
import dev.evasex.ewaira.verification.TerrainCommandWorldStats;
import java.nio.ByteBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TerrainDrawCommands.class)
abstract class WorldCommandUploadMixin {
    @Inject(method = "copyCachedBytes", at = @At("RETURN"))
    private void ewaira$countReuse(ByteBuffer target, int offset, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) {
            int count = ((TerrainDrawCommands) (Object) this).size();
            TerrainCommandWorldStats.reuse((long) count * TerrainDrawCommands.COMMAND_BYTES);
        }
    }
}
