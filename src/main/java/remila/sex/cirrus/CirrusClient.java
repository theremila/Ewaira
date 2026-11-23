package remila.sex.cirrus;

import remila.sex.cirrus.config.ConfigManager;
import remila.sex.cirrus.config.ConfigStore;
import java.io.IOException;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class CirrusClient implements ClientModInitializer {
    private static final ConfigManager CONFIG = new ConfigManager(
            ConfigStore.inDirectory(FabricLoader.getInstance().getConfigDir()));

    public static ConfigManager config() {
        return CONFIG;
    }

    @Override
    public void onInitializeClient() {
        try {
            CONFIG.load();
        } catch (IOException failure) {
            ModIdentity.LOGGER.warn("Could not load settings; using defaults. The existing file was preserved.",
                    failure);
        }
    }
}
