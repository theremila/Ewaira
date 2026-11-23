package remila.sex.cirrus.mixin;

import remila.sex.cirrus.terrain.TerrainSectionCache;
import remila.sex.cirrus.terrain.TerrainSliceAccess;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CompiledSectionMesh.class)
abstract class TerrainSectionCacheMixin implements TerrainSliceAccess {
    @Unique private final TerrainSectionCache cirrus$sectionCache = new TerrainSectionCache();
    @Override public TerrainSectionCache cirrus$sectionCache() { return cirrus$sectionCache; }
    @Inject(method = "close", at = @At("HEAD"))
    private void cirrus$releaseBindings(CallbackInfo ci) { cirrus$sectionCache.clear(); }
}
