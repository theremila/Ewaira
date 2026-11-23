package remila.sex.cirrus.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Loaded by Mod Menu's entrypoint only; Mod Menu is never required at runtime. */
public final class CirrusModMenu implements ModMenuApi {
    @Override public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return CirrusConfigScreen::new;
    }
}
