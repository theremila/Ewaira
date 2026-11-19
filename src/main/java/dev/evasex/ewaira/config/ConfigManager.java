package dev.evasex.ewaira.config;

import dev.evasex.ewaira.ModIdentity;
import dev.evasex.ewaira.debug.RenderDiagnostics;
import dev.evasex.ewaira.culling.BlockEntityCulling;
import dev.evasex.ewaira.lod.FarTerrainLod;
import dev.evasex.ewaira.lod.ItemLod;
import dev.evasex.ewaira.lod.TerrainLod;
import dev.evasex.ewaira.mesh.ChunkLighting;
import dev.evasex.ewaira.mesh.FaceVisibility;
import dev.evasex.ewaira.mesh.QuadEncoding;
import dev.evasex.ewaira.mesh.SectionVisibility;
import dev.evasex.ewaira.model.ModelGeometry;
import dev.evasex.ewaira.particle.ParticleCulling;
import dev.evasex.ewaira.terrain.TerrainOptimization;
import dev.evasex.ewaira.terrain.TerrainBatching;
import dev.evasex.ewaira.text.TextOptimization;
import dev.evasex.ewaira.startup.TexturePreparation;
import java.io.IOException;
import java.nio.file.Path;

/** Loaded and edited on the client thread; no configuration I/O runs during rendering. */
public final class ConfigManager {
    private final ConfigStore store;
    private EwairaSettings settings = EwairaSettings.defaults();

    public ConfigManager(Path path) { this(new ConfigStore(path)); }
    public ConfigManager(ConfigStore store) { this.store = store; }
    public EwairaSettings settings() { return settings; }
    public void load() throws IOException {
        settings = store.load();
        apply(settings);
    }
    public void save(EwairaSettings next) throws IOException {
        store.save(next);
        settings = next;
        apply(next);
    }
    private static void apply(EwairaSettings next) {
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
        TerrainOptimization.setMargin(EwairaSettings.DEFAULT_MARGIN);
        TerrainOptimization.setEnabled(allowed && next.active(Optimization.TERRAIN_CULLING));
        ParticleCulling.setEnabled(allowed && next.active(Optimization.PARTICLE_CULLING));
        BlockEntityCulling.setEnabled(allowed && next.active(Optimization.BLOCK_ENTITY_CULLING));
    }
}
