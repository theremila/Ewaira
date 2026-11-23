package remila.sex.cirrus;

import remila.sex.cirrus.config.ConfigStore;
import remila.sex.cirrus.config.CirrusSettings;
import remila.sex.cirrus.config.WorldGenerationSettings;
import java.io.IOException;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/** Dedicated servers load only common policies, never client rendering classes. */
public final class CirrusServer implements DedicatedServerModInitializer {
    @Override public void onInitializeServer() {
        initialize(ConfigStore.inDirectory(FabricLoader.getInstance().getConfigDir()));
    }

    static void initialize(ConfigStore store) {
        CirrusSettings settings = CirrusSettings.defaults();
        try { settings = store.load(); }
        catch (IOException failure) {
            ModIdentity.LOGGER.warn("Invalid server config; using defaults without changing the file", failure);
        }
        WorldGenerationSettings.apply(settings, !ModIdentity.disabledBySystemProperty());
    }
}
