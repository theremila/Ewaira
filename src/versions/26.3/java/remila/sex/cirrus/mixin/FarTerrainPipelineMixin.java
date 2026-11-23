package remila.sex.cirrus.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import remila.sex.cirrus.lod.FarTerrainRenderer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderPipelines.class)
abstract class FarTerrainPipelineMixin {
    @ModifyReturnValue(method = "optionalPipelines", at = @At("RETURN"))
    private static List<RenderPipeline> cirrus$registerFarTerrain(List<RenderPipeline> original) {
        var pipelines = new ArrayList<>(original);
        pipelines.add(FarTerrainRenderer.PIPELINE);
        return pipelines;
    }
}
