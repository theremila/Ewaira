package remila.sex.cirrus.mixin;

import com.mojang.blaze3d.vertex.MeshData;
import remila.sex.cirrus.terrain.TerrainMeshAccess;
import remila.sex.cirrus.terrain.TerrainMeshes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionCompiler.class)
abstract class TerrainSectionCompilerMixin {
    @Inject(method = "compile", at = @At("RETURN"))
    private void cirrus$groupSolid(CallbackInfoReturnable<SectionCompiler.Results> cir) {
        MeshData mesh = cir.getReturnValue().renderedLayers.get(RenderType.solid());
        if (mesh != null) ((TerrainMeshAccess) mesh).cirrus$terrainFaces(TerrainMeshes.sort(mesh));
    }
}
