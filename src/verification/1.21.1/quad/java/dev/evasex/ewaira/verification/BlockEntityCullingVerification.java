package dev.evasex.ewaira.verification;

import dev.evasex.ewaira.mixin.FrustumInvoker;
import java.util.Arrays;
import java.util.Random;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

public final class BlockEntityCullingVerification {
    private BlockEntityCullingVerification() {}

    public static String verify() {
        if (Arrays.stream(BlockEntityRenderDispatcher.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().contains("ewaira$skipOutsideView"))) {
            throw new AssertionError("Block-entity culling injection missing");
        }
        Random random = new Random(81932);
        int rejected = 0;
        for (int i = 0; i < 10000; i++) {
            Frustum frustum = new Frustum(new Matrix4f().rotateY(i * 0.01f),
                    new Matrix4f().perspective(1.2f, 1.7f, 0.1f, 128));
            double cameraX = i % 2 == 0 ? -100003 : 0;
            frustum.prepare(cameraX, 64, 100007);
            double x = cameraX + random.nextInt(-150, 150);
            double y = 64 + random.nextInt(-150, 150);
            double z = 100007 + random.nextInt(-150, 150);
            boolean expected = frustum.isVisible(new AABB(x - 2, y - 2, z - 2, x + 3, y + 3, z + 3));
            boolean actual = ((FrustumInvoker) frustum).ewaira$containsBox(x - 2, y - 2, z - 2, x + 3, y + 3, z + 3);
            if (expected != actual) throw new AssertionError("Block-entity frustum mismatch");
            if (!actual) rejected++;
        }
        return "PASS: block-entity dispatcher injection loaded; 10000 allocation-free frustum checks match vanilla AABB tests, rejected="
                + rejected + ". Model animations/visuals require an in-world check.\n";
    }
}
