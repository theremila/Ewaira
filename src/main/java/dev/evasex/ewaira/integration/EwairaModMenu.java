package dev.evasex.ewaira.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Loaded by Mod Menu's entrypoint only; Mod Menu is never required at runtime. */
public final class EwairaModMenu implements ModMenuApi {
    @Override public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return EwairaConfigScreen::new;
    }
}
