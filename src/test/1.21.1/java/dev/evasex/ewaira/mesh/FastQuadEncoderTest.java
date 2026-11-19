package dev.evasex.ewaira.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.evasex.ewaira.verification.QuadEncodingVerification;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FastQuadEncoderTest {
    @Test void matchesVanillaNativeVertexBuffers() {
        assertTrue(QuadEncodingVerification.verify(false).startsWith("PASS:"));
    }

    @Test void matchesVanillaPositionAndNormalTransforms() {
        assertTrue(QuadEncodingVerification.verifyTransforms(false).startsWith("PASS:"));
    }

    @Test void declinesNonstandardLayoutsAndTruncatedInputs() {
        FastQuadEncoder encoder = new FastQuadEncoder();
        BakedQuad standard = new BakedQuad(new int[32], -1, Direction.UP, null, true);
        assertTrue(encoder.accepts(standard, new float[4], new int[4]));
        assertFalse(encoder.accepts(standard, new float[3], new int[4]));
        assertFalse(encoder.accepts(standard, new float[4], new int[3]));
        assertFalse(encoder.accepts(new BakedQuad(new int[40], -1, Direction.UP, null, true), new float[5], new int[5]));
    }
}
