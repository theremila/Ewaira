package dev.evasex.ewaira.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.evasex.ewaira.mesh.FastQuadEncoder;
import dev.evasex.ewaira.mesh.QuadEncoding;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BufferBuilder.class)
abstract class BufferBuilderMixin implements VertexConsumer {
    @Unique private final FastQuadEncoder ewaira$encoder = new FastQuadEncoder();
    @Unique private boolean ewaira$encoding;

    @Override
    public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness,
            float red, float green, float blue, float alpha, int[] lights, int overlay, boolean useQuadColor) {
        if (!QuadEncoding.isEnabled() || ewaira$encoding || !ewaira$encoder.accepts(quad, brightness, lights)) {
            VertexConsumer.super.putBulkData(pose, quad, brightness, red, green, blue, alpha, lights, overlay, useQuadColor);
            return;
        }
        ewaira$encoding = true;
        try {
            ewaira$encoder.write(this, pose, quad, brightness, red, green, blue, alpha, lights, overlay, useQuadColor);
        } finally {
            ewaira$encoding = false;
        }
    }

    @Override
    public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float red, float green, float blue,
            float alpha, int light, int overlay) {
        if (!QuadEncoding.isEnabled() || ewaira$encoding || !ewaira$encoder.accepts(quad)) {
            VertexConsumer.super.putBulkData(pose, quad, red, green, blue, alpha, light, overlay);
            return;
        }
        ewaira$encoding = true;
        try {
            ewaira$encoder.writeUniform(this, pose, quad, red, green, blue, alpha, light, overlay);
        } finally { ewaira$encoding = false; }
    }

    @Override
    public VertexConsumer addVertex(Matrix4f transform, float x, float y, float z) {
        if (!QuadEncoding.isEnabled() || ewaira$encoding) return VertexConsumer.super.addVertex(transform, x, y, z);
        ewaira$encoding = true;
        try { return ewaira$encoder.writePosition(this, transform, x, y, z); }
        finally { ewaira$encoding = false; }
    }

    @Override
    public VertexConsumer setNormal(PoseStack.Pose pose, float x, float y, float z) {
        if (!QuadEncoding.isEnabled() || ewaira$encoding) return VertexConsumer.super.setNormal(pose, x, y, z);
        ewaira$encoding = true;
        try { return ewaira$encoder.writeNormal(this, pose, x, y, z); }
        finally { ewaira$encoding = false; }
    }

}
