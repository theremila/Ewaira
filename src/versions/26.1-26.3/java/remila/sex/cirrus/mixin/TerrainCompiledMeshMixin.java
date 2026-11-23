package remila.sex.cirrus.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import remila.sex.cirrus.terrain.TerrainFaces;
import remila.sex.cirrus.terrain.TerrainMeshAccess;
import remila.sex.cirrus.terrain.TerrainMeshes;
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
    @Unique private TerrainFaces cirrus$faces;
    @Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;renderedLayers:Ljava/util/Map;"))
    private void cirrus$groupSolid(CallbackInfo ci, @Local(argsOnly = true) SectionCompiler.Results results) {
        cirrus$terrainFaces(TerrainMeshes.sort(results.renderedLayers.get(ChunkSectionLayer.SOLID)));
    }
    @Override public TerrainFaces cirrus$terrainFaces() { return cirrus$faces; }
    @Override public void cirrus$terrainFaces(TerrainFaces faces) { cirrus$faces = faces; }
}
