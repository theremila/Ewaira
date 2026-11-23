package remila.sex.cirrus;

import remila.sex.cirrus.config.ConfigStore;
import remila.sex.cirrus.config.CirrusSettings;
import remila.sex.cirrus.config.WorldGenerationSettings;
import remila.sex.cirrus.worldgen.BiomeSampling;
import remila.sex.cirrus.worldgen.DensityBufferReuse;
import remila.sex.cirrus.worldgen.PalettePacking;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CirrusServerTest {
    @TempDir Path configDirectory;

    @AfterEach void restoreWorldGenerationSettings() {
        WorldGenerationSettings.apply(CirrusSettings.defaults(), !ModIdentity.disabledBySystemProperty());
    }

    @Test void dedicatedServerHonorsWorldGenerationSwitches() throws IOException {
        Path config = configDirectory.resolve(ModIdentity.CONFIG_FILE);
        ConfigStore store = ConfigStore.inDirectory(configDirectory);

        CirrusServer.initialize(store);
        assertTrue(DensityBufferReuse.isEnabled());

        Files.writeString(config, "{\"modules\":{\"density_buffers\":false}}");
        CirrusServer.initialize(store);
        assertTrue(BiomeSampling.isEnabled());
        assertTrue(PalettePacking.isEnabled());
        assertFalse(DensityBufferReuse.isEnabled());

        Files.writeString(config, "{\"enabled\":false,\"modules\":{\"density_buffers\":true}}");
        CirrusServer.initialize(store);
        assertFalse(BiomeSampling.isEnabled());
        assertFalse(PalettePacking.isEnabled());
        assertFalse(DensityBufferReuse.isEnabled());
    }
}
