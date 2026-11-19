package dev.evasex.ewaira.mixin;

import dev.evasex.ewaira.lod.FarTerrainAccess;
import dev.evasex.ewaira.lod.FarTerrainCache;
import dev.evasex.ewaira.lod.FarTerrainLod;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
abstract class FarTerrainCaptureMixin implements FarTerrainAccess {
    @Unique private final FarTerrainCache ewaira$farTerrain = new FarTerrainCache();

    @Override
    public FarTerrainCache ewaira$farTerrain() {
        return ewaira$farTerrain;
    }

    @Inject(method = "unload", at = @At("HEAD"))
    private void ewaira$captureSurface(LevelChunk chunk, CallbackInfo ci) {
        ClientLevel level = (ClientLevel) (Object) this;
        if (FarTerrainLod.isEnabled() && level.dimensionType().hasSkyLight() && !chunk.isEmpty()) {
            ewaira$farTerrain.remember(chunk);
        }
    }
}
