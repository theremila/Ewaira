package dev.evasex.ewaira.mixin;

import dev.evasex.ewaira.terrain.TerrainSectionCache;
import dev.evasex.ewaira.terrain.TerrainSliceAccess;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CompiledSectionMesh.class)
abstract class TerrainSectionCacheMixin implements TerrainSliceAccess {
    @Unique private final TerrainSectionCache ewaira$sectionCache = new TerrainSectionCache();
    @Override public TerrainSectionCache ewaira$sectionCache() { return ewaira$sectionCache; }
    @Inject(method = "close", at = @At("HEAD"))
    private void ewaira$releaseBindings(CallbackInfo ci) { ewaira$sectionCache.clear(); }
}
