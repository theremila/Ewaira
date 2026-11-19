package dev.evasex.ewaira;

import dev.evasex.ewaira.config.ConfigStore;
import dev.evasex.ewaira.config.EwairaSettings;
import dev.evasex.ewaira.config.WorldGenerationSettings;
import dev.evasex.ewaira.worldgen.BiomeSampling;
import dev.evasex.ewaira.worldgen.DensityBufferReuse;
import dev.evasex.ewaira.worldgen.PalettePacking;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EwairaServerTest {
    @TempDir Path configDirectory;

    @AfterEach void restoreWorldGenerationSettings() {
        WorldGenerationSettings.apply(EwairaSettings.defaults(), !ModIdentity.disabledBySystemProperty());
    }

    @Test void dedicatedServerHonorsWorldGenerationSwitches() throws IOException {
        Path config = configDirectory.resolve(ModIdentity.CONFIG_FILE);
        ConfigStore store = ConfigStore.inDirectory(configDirectory);

        EwairaServer.initialize(store);
        assertTrue(DensityBufferReuse.isEnabled());

        Files.writeString(config, "{\"modules\":{\"density_buffers\":false}}");
        EwairaServer.initialize(store);
        assertTrue(BiomeSampling.isEnabled());
        assertTrue(PalettePacking.isEnabled());
        assertFalse(DensityBufferReuse.isEnabled());

        Files.writeString(config, "{\"enabled\":false,\"modules\":{\"density_buffers\":true}}");
        EwairaServer.initialize(store);
        assertFalse(BiomeSampling.isEnabled());
        assertFalse(PalettePacking.isEnabled());
        assertFalse(DensityBufferReuse.isEnabled());
    }
}
