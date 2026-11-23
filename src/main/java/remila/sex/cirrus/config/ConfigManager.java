package remila.sex.cirrus.config;

import remila.sex.cirrus.ModIdentity;
import remila.sex.cirrus.debug.RenderDiagnostics;
import remila.sex.cirrus.culling.BlockEntityCulling;
import remila.sex.cirrus.culling.EntityOcclusionEngine;
import remila.sex.cirrus.lod.FarTerrainLod;
import remila.sex.cirrus.lod.ItemLod;
import remila.sex.cirrus.lod.TerrainLod;
import remila.sex.cirrus.mesh.ChunkLighting;
import remila.sex.cirrus.mesh.FaceVisibility;
import remila.sex.cirrus.mesh.QuadEncoding;
import remila.sex.cirrus.mesh.SectionVisibility;
import remila.sex.cirrus.model.ModelGeometry;
import remila.sex.cirrus.particle.ParticleCulling;
import remila.sex.cirrus.terrain.TerrainOptimization;
import remila.sex.cirrus.terrain.TerrainBatching;
import remila.sex.cirrus.text.TextOptimization;
import remila.sex.cirrus.startup.TexturePreparation;
import java.io.IOException;
import java.nio.file.Path;

/** Loaded and edited on the client thread; no configuration I/O runs during rendering. */
public final class ConfigManager {
    private final ConfigStore store;
    private CirrusSettings settings = CirrusSettings.defaults();

    public ConfigManager(Path path) { this(new ConfigStore(path)); }
    public ConfigManager(ConfigStore store) { this.store = store; }
    public CirrusSettings settings() { return settings; }
    public void load() throws IOException {
        settings = store.load();
        apply(settings);
    }
    public void save(CirrusSettings next) throws IOException {
        store.save(next);
        settings = next;
        apply(next);
    }
    private static void apply(CirrusSettings next) {
        boolean allowed = !ModIdentity.disabledBySystemProperty();
        RenderDiagnostics.setEnabled(allowed && next.debug());
        TexturePreparation.setEnabled(allowed && next.active(Optimization.TEXTURE_PREPARATION));
        TerrainBatching.setEnabled(allowed && next.active(Optimization.CHUNK_BATCHING));
        TerrainLod.setEnabled(allowed && next.active(Optimization.TERRAIN_LOD));
        FarTerrainLod.setEnabled(allowed && next.active(Optimization.FAR_TERRAIN));
        WorldGenerationSettings.apply(next, allowed);
        ItemLod.setEnabled(allowed && next.active(Optimization.ITEM_LOD));
        TextOptimization.setEnabled(allowed && next.active(Optimization.TEXT_CACHE));
        QuadEncoding.setEnabled(allowed && next.active(Optimization.QUAD_ENCODING));
        SectionVisibility.setEnabled(allowed && next.active(Optimization.SECTION_VISIBILITY));
        ChunkLighting.setEnabled(allowed && next.active(Optimization.SECTION_LIGHTING));
        ModelGeometry.setEnabled(allowed && next.active(Optimization.MODEL_BATCHING));
        FaceVisibility.setEnabled(allowed && next.active(Optimization.FACE_CACHE));
        TerrainOptimization.setMargin(CirrusSettings.DEFAULT_MARGIN);
        TerrainOptimization.setEnabled(allowed && next.active(Optimization.TERRAIN_CULLING));
        EntityOcclusionEngine.setEnabled(allowed && next.active(Optimization.ENTITY_OCCLUSION));
        ParticleCulling.setEnabled(allowed && next.active(Optimization.PARTICLE_CULLING));
        BlockEntityCulling.setEnabled(allowed && next.active(Optimization.BLOCK_ENTITY_CULLING));
    }
}
