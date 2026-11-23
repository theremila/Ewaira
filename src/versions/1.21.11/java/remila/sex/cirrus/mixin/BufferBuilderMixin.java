package remila.sex.cirrus.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import remila.sex.cirrus.mesh.FastQuadEncoder;
import remila.sex.cirrus.mesh.QuadEncoding;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BufferBuilder.class)
abstract class BufferBuilderMixin implements VertexConsumer {
    @Unique private final FastQuadEncoder cirrus$encoder = new FastQuadEncoder();
    @Unique private boolean cirrus$encoding;

    @Override
    public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness,
            float red, float green, float blue, float alpha, int[] lights, int overlay) {
        if (!QuadEncoding.isEnabled() || cirrus$encoding || !cirrus$encoder.accepts(quad, brightness, lights)) {
            VertexConsumer.super.putBulkData(pose, quad, brightness, red, green, blue, alpha, lights, overlay);
            return;
        }
        cirrus$encoding = true;
        try {
            cirrus$encoder.write(this, pose, quad, brightness, red, green, blue, alpha, lights, overlay);
        } finally {
            cirrus$encoding = false;
        }
    }

    @Override
    public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float red, float green, float blue,
            float alpha, int light, int overlay) {
        if (!QuadEncoding.isEnabled() || cirrus$encoding) {
            VertexConsumer.super.putBulkData(pose, quad, red, green, blue, alpha, light, overlay);
            return;
        }
        cirrus$encoding = true;
        try {
            cirrus$encoder.writeUniform(this, pose, quad, red, green, blue, alpha, light, overlay);
        } finally { cirrus$encoding = false; }
    }

    @Override
    public VertexConsumer addVertex(Matrix4fc transform, float x, float y, float z) {
        if (!QuadEncoding.isEnabled() || cirrus$encoding) return VertexConsumer.super.addVertex(transform, x, y, z);
        cirrus$encoding = true;
        try { return cirrus$encoder.writePosition(this, transform, x, y, z); }
        finally { cirrus$encoding = false; }
    }

    @Override
    public VertexConsumer setNormal(PoseStack.Pose pose, float x, float y, float z) {
        if (!QuadEncoding.isEnabled() || cirrus$encoding) return VertexConsumer.super.setNormal(pose, x, y, z);
        cirrus$encoding = true;
        try { return cirrus$encoder.writeNormal(this, pose, x, y, z); }
        finally { cirrus$encoding = false; }
    }

}
