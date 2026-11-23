package remila.sex.cirrus.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import remila.sex.cirrus.mesh.ModernQuadEncoder;
import remila.sex.cirrus.mesh.QuadEncoding;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BufferBuilder.class)
abstract class BufferBuilderMixin implements VertexConsumer {
    @Unique private final ModernQuadEncoder cirrus$encoder = new ModernQuadEncoder();
    @Unique private boolean cirrus$encoding;

    @Override
    public void putBakedQuad(PoseStack.Pose pose, BakedQuad quad, QuadInstance instance) {
        if (!QuadEncoding.isEnabled() || cirrus$encoding) {
            VertexConsumer.super.putBakedQuad(pose, quad, instance);
            return;
        }
        cirrus$encoding = true;
        try { cirrus$encoder.write(this, pose, quad, instance); }
        finally { cirrus$encoding = false; }
    }

    @Override
    public VertexConsumer addVertex(Matrix4fc matrix, float x, float y, float z) {
        if (!QuadEncoding.isEnabled() || cirrus$encoding) return VertexConsumer.super.addVertex(matrix, x, y, z);
        cirrus$encoding = true;
        try { return cirrus$encoder.writePosition(this, matrix, x, y, z); }
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
