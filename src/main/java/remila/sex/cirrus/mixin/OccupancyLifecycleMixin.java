package remila.sex.cirrus.mixin;

import remila.sex.cirrus.culling.EntityOcclusionEngine;
import remila.sex.cirrus.culling.SectionOccupancyMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
abstract class OccupancyLifecycleMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void cirrus$bindWorld(CallbackInfo ci) {
        SectionOccupancyMap.ensureWorld(this);
        EntityOcclusionEngine.invalidateCache();
    }

    @Inject(method = "unload", at = @At("HEAD"))
    private void cirrus$evictChunkOccupancy(LevelChunk chunk, CallbackInfo ci) {
        if (chunk == null) return;
        ChunkPos pos = chunk.getPos();
        int count = chunk.getSectionsCount();
        if (count <= 0) return;
        int minSectionY = chunk.getSectionYFromSectionIndex(0);
        SectionOccupancyMap.removeColumn(pos.getMinBlockX() >> 4, pos.getMinBlockZ() >> 4,
                minSectionY, minSectionY + count - 1);
    }
}
