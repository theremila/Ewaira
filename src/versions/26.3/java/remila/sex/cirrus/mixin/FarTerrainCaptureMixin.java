package remila.sex.cirrus.mixin;

import remila.sex.cirrus.lod.FarTerrainAccess;
import remila.sex.cirrus.lod.FarTerrainCache;
import remila.sex.cirrus.lod.FarTerrainLod;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
abstract class FarTerrainCaptureMixin implements FarTerrainAccess {
    @Unique private final FarTerrainCache cirrus$farTerrain = new FarTerrainCache();

    @Override
    public FarTerrainCache cirrus$farTerrain() {
        return cirrus$farTerrain;
    }

    @Inject(method = "unload", at = @At("HEAD"))
    private void cirrus$captureSurface(LevelChunk chunk, CallbackInfo ci) {
        ClientLevel level = (ClientLevel) (Object) this;
        if (FarTerrainLod.isEnabled() && level.dimensionType().hasSkyLight() && !chunk.isEmpty()) {
            cirrus$farTerrain.remember(chunk);
        }
    }
}
