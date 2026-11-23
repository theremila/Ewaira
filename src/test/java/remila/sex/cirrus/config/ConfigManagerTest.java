package remila.sex.cirrus.config;

import remila.sex.cirrus.ModIdentity;
import remila.sex.cirrus.debug.RenderDiagnostics;
import remila.sex.cirrus.culling.BlockEntityCulling;
import remila.sex.cirrus.culling.EntityOcclusionEngine;
import remila.sex.cirrus.lod.ItemLod;
import remila.sex.cirrus.lod.TerrainLod;
import remila.sex.cirrus.worldgen.BiomeSampling;
import remila.sex.cirrus.mesh.ChunkLighting;
import remila.sex.cirrus.mesh.FaceVisibility;
import remila.sex.cirrus.mesh.QuadEncoding;
import remila.sex.cirrus.mesh.SectionVisibility;
import remila.sex.cirrus.model.ModelGeometry;
import remila.sex.cirrus.particle.ParticleCulling;
import remila.sex.cirrus.terrain.TerrainOptimization;
import remila.sex.cirrus.text.TextOptimization;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {
    @TempDir Path directory;

    @AfterEach void restoreModules() throws IOException {
        new ConfigManager(directory.resolve("restore.json")).save(CirrusSettings.defaults());
    }

    @Test void missingFileEnablesEverythingWithoutCreatingFiles() throws IOException {
        Path path = directory.resolve("cirrus.json");
        assertEquals(CirrusSettings.defaults(), new ConfigStore(path).load());
        assertFalse(Files.exists(path));
    }

    @Test void roundTripPreservesEveryModule() throws IOException {
        CirrusSettings settings = CirrusSettings.defaults();
        for (Optimization module : Optimization.values()) settings = settings.withModule(module, module.ordinal() % 2 == 0);
        ConfigStore store = new ConfigStore(directory.resolve("nested/cirrus.json"));
        store.save(settings);
        assertEquals(settings, store.load());
        try (var files = Files.list(directory.resolve("nested"))) { assertEquals(1, files.count()); }
    }

    @Test void missingKeysUseDefaultsAndMalformedFilesStayUntouched() throws IOException {
        Path path = directory.resolve("cirrus.json");
        Files.writeString(path, "{\"modules\":{\"text_cache\":false}}");
        CirrusSettings loaded = new ConfigStore(path).load();
        assertFalse(loaded.debug());
        assertFalse(loaded.active(Optimization.TEXT_CACHE));
        assertTrue(loaded.active(Optimization.TERRAIN_CULLING));
        assertTrue(loaded.active(Optimization.ITEM_LOD));
        assertTrue(loaded.active(Optimization.TERRAIN_LOD));
        for (String invalid : new String[]{"{", "null", "[]", "{\"enabled\":\"false\"}",
                "{\"schema\":99}", "{\"terrain_margin\":0}", "{\"terrain_margin\":\"NaN\"}", "{\"terrain_lod_distance\":0}", "{\"terrain_lod_distance\":\"NaN\"}", "{\"item_lod_distance\":0}", "{\"item_lod_distance\":\"NaN\"}"}) {
            Files.writeString(path, invalid);
            assertThrows(IOException.class, () -> new ConfigStore(path).load());
            assertEquals(invalid, Files.readString(path));
        }
    }

    @Test void settingsAreImmutableAndConservative() {
        Map<Optimization, Boolean> modules = new HashMap<>();
        modules.put(Optimization.TEXT_CACHE, false);
        CirrusSettings settings = new CirrusSettings(true, false, modules);
        modules.clear();
        assertFalse(settings.active(Optimization.TEXT_CACHE));
        assertThrows(UnsupportedOperationException.class, () -> settings.modules().clear());
        assertTrue(Optimization.CHUNK_BATCHING.supports("26.3"));
        assertFalse(Optimization.CHUNK_BATCHING.supports("26.2"));
        assertFalse(Optimization.PARTICLE_CULLING.supports(false));
        assertTrue(Optimization.PARTICLE_CULLING.supports(true));
    }

    @Test void savedChangesApplyImmediatelyAndMasterSwitchPreservesChoices() throws IOException {
        ConfigManager manager = new ConfigManager(directory.resolve("cirrus.json"));
        manager.load();
        CirrusSettings settings = manager.settings().withModule(Optimization.TEXT_CACHE, false);
        manager.save(settings);
        assertFalse(TextOptimization.isEnabled());
        assertTrue(TerrainOptimization.isEnabled());
        assertEquals(CirrusSettings.DEFAULT_MARGIN, TerrainOptimization.margin());
        manager.save(settings.withEnabled(false));
        assertFalse(ItemLod.enabled()); assertFalse(TerrainLod.isEnabled());
        assertFalse(BiomeSampling.isEnabled());
        assertFalse(SectionVisibility.isEnabled());
        assertFalse(QuadEncoding.isEnabled()); assertFalse(ChunkLighting.isEnabled());
        assertFalse(ModelGeometry.isEnabled()); assertFalse(FaceVisibility.isEnabled());
        assertFalse(TerrainOptimization.isEnabled()); assertFalse(EntityOcclusionEngine.isEnabled());
        assertFalse(ParticleCulling.isEnabled());
        assertFalse(BlockEntityCulling.isEnabled());
        manager.save(manager.settings().withEnabled(true));
        assertFalse(TextOptimization.isEnabled()); assertTrue(QuadEncoding.isEnabled());
        assertTrue(ItemLod.enabled()); assertTrue(TerrainLod.isEnabled());
        assertTrue(BiomeSampling.isEnabled());
        assertTrue(SectionVisibility.isEnabled());
        assertTrue(ChunkLighting.isEnabled()); assertTrue(ModelGeometry.isEnabled());
        assertTrue(FaceVisibility.isEnabled()); assertTrue(ParticleCulling.isEnabled());
        assertTrue(BlockEntityCulling.isEnabled()); assertTrue(TerrainOptimization.isEnabled());
        assertTrue(EntityOcclusionEngine.isEnabled());
    }

    @Test void migratesOldNumericOptionsWithoutLosingSwitches() throws IOException {
        Path path = directory.resolve(ModIdentity.CONFIG_FILE);
        Path legacy = directory.resolve(ModIdentity.LEGACY_MOD_ID + ".json");
        String original = "{\"schema\":1,\"terrain_margin\":0.25,\"item_lod_distance\":64,\"terrain_lod_distance\":96,\"modules\":{\"terrain_lod\":false}}";
        Files.writeString(legacy, original);
        ConfigStore store = ConfigStore.inDirectory(directory);
        CirrusSettings settings = store.load();
        assertFalse(settings.active(Optimization.TERRAIN_LOD));
        assertFalse(Files.exists(path));
        store.save(settings);
        String json = Files.readString(path);
        assertTrue(json.contains("2"));
        assertFalse(json.contains("terrain_margin"));
        assertFalse(json.contains("lod_distance"));
        assertEquals(settings, store.load());
        store.save(settings.withModule(Optimization.TERRAIN_LOD, true));
        assertTrue(store.load().active(Optimization.TERRAIN_LOD));
        assertEquals(original, Files.readString(legacy));
        Files.writeString(path, "{");
        assertThrows(IOException.class, store::load);
    }

    @Test void prefersPreviousConfigAndSavesUnderCurrentName() throws IOException {
        Path previous = directory.resolve(ModIdentity.PREVIOUS_MOD_ID + ".json");
        Path legacy = directory.resolve(ModIdentity.LEGACY_MOD_ID + ".json");
        String original = "{\"debug\":true,\"modules\":{\"terrain_lod\":false}}";
        Files.writeString(previous, original);
        Files.writeString(legacy, "{\"enabled\":false}");

        ConfigStore store = ConfigStore.inDirectory(directory);
        CirrusSettings settings = store.load();
        assertTrue(settings.enabled());
        assertTrue(settings.debug());
        assertFalse(settings.active(Optimization.TERRAIN_LOD));
        assertFalse(Files.exists(directory.resolve(ModIdentity.CONFIG_FILE)));

        CirrusSettings saved = settings.withDebug(false);
        store.save(saved);
        assertEquals(saved, store.load());
        assertEquals(original, Files.readString(previous));
        assertEquals("{\"enabled\":false}", Files.readString(legacy));
    }

    @Test void debugPersistsIndependentlyOfOptimizations() throws IOException {
        var manager = new ConfigManager(directory.resolve("cirrus.json"));
        manager.load();
        assertFalse(RenderDiagnostics.enabled());
        manager.save(manager.settings().withDebug(true).withEnabled(false));
        manager.load();
        assertTrue(manager.settings().debug());
        assertFalse(manager.settings().enabled());
        assertTrue(RenderDiagnostics.enabled());
        manager.save(CirrusSettings.defaults());
        assertFalse(RenderDiagnostics.enabled());
    }

    @Test void failedSaveDoesNotChangeActiveSettings() throws IOException {
        Path path = directory.resolve("is-a-directory");
        Files.createDirectory(path);
        ConfigManager manager = new ConfigManager(path);
        assertThrows(IOException.class, () -> manager.save(CirrusSettings.defaults().withEnabled(false)));
        assertTrue(manager.settings().enabled());
        assertTrue(QuadEncoding.isEnabled());
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
}
