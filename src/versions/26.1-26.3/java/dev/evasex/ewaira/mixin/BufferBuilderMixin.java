package dev.evasex.ewaira.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.evasex.ewaira.mesh.ModernQuadEncoder;
import dev.evasex.ewaira.mesh.QuadEncoding;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BufferBuilder.class)
abstract class BufferBuilderMixin implements VertexConsumer {
    @Unique private final ModernQuadEncoder ewaira$encoder = new ModernQuadEncoder();
    @Unique private boolean ewaira$encoding;

    @Override
    public void putBakedQuad(PoseStack.Pose pose, BakedQuad quad, QuadInstance instance) {
        if (!QuadEncoding.isEnabled() || ewaira$encoding) {
            VertexConsumer.super.putBakedQuad(pose, quad, instance);
            return;
        }
        ewaira$encoding = true;
        try { ewaira$encoder.write(this, pose, quad, instance); }
        finally { ewaira$encoding = false; }
    }

    @Override
    public VertexConsumer addVertex(Matrix4fc matrix, float x, float y, float z) {
        if (!QuadEncoding.isEnabled() || ewaira$encoding) return VertexConsumer.super.addVertex(matrix, x, y, z);
        ewaira$encoding = true;
        try { return ewaira$encoder.writePosition(this, matrix, x, y, z); }
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
