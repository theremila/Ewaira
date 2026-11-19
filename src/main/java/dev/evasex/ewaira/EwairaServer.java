package dev.evasex.ewaira;

import dev.evasex.ewaira.config.ConfigStore;
import dev.evasex.ewaira.config.EwairaSettings;
import dev.evasex.ewaira.config.WorldGenerationSettings;
import java.io.IOException;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/** Dedicated servers load only common policies, never client rendering classes. */
public final class EwairaServer implements DedicatedServerModInitializer {
    @Override public void onInitializeServer() {
        initialize(ConfigStore.inDirectory(FabricLoader.getInstance().getConfigDir()));
    }

    static void initialize(ConfigStore store) {
        EwairaSettings settings = EwairaSettings.defaults();
        try { settings = store.load(); }
        catch (IOException failure) {
            ModIdentity.LOGGER.warn("Invalid server config; using defaults without changing the file", failure);
        }
        WorldGenerationSettings.apply(settings, !ModIdentity.disabledBySystemProperty());
    }
}
