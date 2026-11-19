package dev.evasex.ewaira.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.evasex.ewaira.terrain.TerrainFaces;
import dev.evasex.ewaira.terrain.TerrainMeshAccess;
import dev.evasex.ewaira.terrain.TerrainMeshes;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CompiledSectionMesh.class)
abstract class TerrainCompiledMeshMixin implements TerrainMeshAccess {
    @Unique private TerrainFaces ewaira$faces;
    @Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;renderedLayers:Ljava/util/Map;"))
    private void ewaira$groupSolid(CallbackInfo ci, @Local(argsOnly = true) SectionCompiler.Results results) {
        ewaira$terrainFaces(TerrainMeshes.sort(results.renderedLayers.get(ChunkSectionLayer.SOLID)));
    }
    @Override public TerrainFaces ewaira$terrainFaces() { return ewaira$faces; }
    @Override public void ewaira$terrainFaces(TerrainFaces faces) { ewaira$faces = faces; }
}
