package remila.sex.cirrus.verification;

import remila.sex.cirrus.mixin.LevelRendererAccessor;

import com.mojang.blaze3d.vertex.VertexConsumer;
import remila.sex.cirrus.particle.ParticleCulling;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Compares actual vanilla vertices and attributes, not a duplicate rendering formula. */
public final class ParticleMeshVerification {
    private ParticleMeshVerification() {}

    public static String verify(Minecraft client) {
        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 origin = camera.getPosition();
        Frustum frustum = ((LevelRendererAccessor) client.levelRenderer).cirrus$getCullingFrustum();
        Random random = new Random(1234);
        SimpleParticleType[] types = {ParticleTypes.FLAME, ParticleTypes.SMOKE, ParticleTypes.CRIT,
                ParticleTypes.END_ROD, ParticleTypes.SNOWFLAKE, ParticleTypes.CLOUD,
                ParticleTypes.BUBBLE, ParticleTypes.SOUL};
        int retained = 0;
        int culled = 0;
        for (int i = 0; i < 2000; i++) {
            Particle particle = client.particleEngine.createParticle(types[i % types.length],
                    origin.x + random.nextDouble(-64, 64), origin.y + random.nextDouble(-64, 64),
                    origin.z + random.nextDouble(-64, 64), 0, 0, 0);
            if (particle == null) throw new AssertionError("Particle factory returned null");
            particle.scale(i % 10 == 0 ? 100 : 1);
            Mesh baseline = new Mesh();
            Mesh optimized = new Mesh();
            ParticleCulling.setEnabled(false);
            particle.render(baseline, camera, 0.5f);
            ParticleCulling.setEnabled(true);
            particle.render(optimized, camera, 0.5f);
            if (optimized.positions.isEmpty()) {
                if (baseline.positions.isEmpty()) throw new AssertionError("No baseline vertices");
                double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
                double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
                for (Vec3 vertex : baseline.positions) {
                    minX = Math.min(minX, origin.x + vertex.x); maxX = Math.max(maxX, origin.x + vertex.x);
                    minY = Math.min(minY, origin.y + vertex.y); maxY = Math.max(maxY, origin.y + vertex.y);
                    minZ = Math.min(minZ, origin.z + vertex.z); maxZ = Math.max(maxZ, origin.z + vertex.z);
                }
                if (frustum.isVisible(new AABB(minX, minY, minZ, maxX, maxY, maxZ))) {
                    throw new AssertionError("Culled a potentially visible vanilla mesh: " + types[i % types.length]);
                }
                culled++;
            } else {
                if (!baseline.attributes.equals(optimized.attributes)) {
                    throw new AssertionError("Changed visible vertices or attributes");
                }
                retained++;
            }
            particle.remove();
        }
        if (culled == 0 || retained == 0) throw new AssertionError("Did not exercise both paths");
        ParticleCulling.setEnabled(true);
        return "PASS: 2000 actual particles, eight vanilla types, ordinary and 100x sizes.\n"
                + "Retained meshes identical including attributes; every skipped mesh AABB outside actual frustum.\n"
                + "retained=" + retained + ", culled=" + culled + "\n"
                + ParticleCulling.statistics() + "\n";
    }

    private static final class Mesh implements VertexConsumer {
        private final List<Vec3> positions = new ArrayList<>();
        private final List<Number> attributes = new ArrayList<>();
        public VertexConsumer addVertex(float x, float y, float z) {
            positions.add(new Vec3(x, y, z)); attributes.add(x); attributes.add(y); attributes.add(z); return this;
        }
        public VertexConsumer setColor(int r, int g, int b, int a) {
            attributes.add(r); attributes.add(g); attributes.add(b); attributes.add(a); return this;
        }
        public VertexConsumer setUv(float u, float v) { attributes.add(u); attributes.add(v); return this; }
        public VertexConsumer setUv1(int u, int v) { attributes.add(u); attributes.add(v); return this; }
        public VertexConsumer setUv2(int u, int v) { attributes.add(u); attributes.add(v); return this; }
        public VertexConsumer setNormal(float x, float y, float z) {
            attributes.add(x); attributes.add(y); attributes.add(z); return this;
        }
    }
}
