package dev.evasex.ewaira.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.evasex.ewaira.model.CubeGeometry;
import dev.evasex.ewaira.model.ModelGeometry;
import dev.evasex.ewaira.model.PackedModelWriter;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.Cube.class)
abstract class ModelCubeMixin {
    @Shadow @Final private ModelPart.Polygon[] polygons;
    @Unique private volatile CubeGeometry ewaira$geometry;

    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void ewaira$compileCorners(PoseStack.Pose pose, VertexConsumer target, int light,
            int overlay, int color, CallbackInfo ci) {
        if (ModelGeometry.isEnabled() && PackedModelWriter.LITTLE_ENDIAN && target.getClass() == BufferBuilder.class) {
            CubeGeometry geometry = ewaira$geometry;
            if (geometry == null || !geometry.matches(polygons)) {
                geometry = new CubeGeometry(polygons);
                ewaira$geometry = geometry;
            }
            if (geometry.render(pose, target, light, overlay, color)) ci.cancel();
        }
    }
}
