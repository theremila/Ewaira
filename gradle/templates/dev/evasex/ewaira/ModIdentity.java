package dev.evasex.ewaira;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Runtime identity generated from Gradle properties. */
public final class ModIdentity {
    public static final String MOD_ID = "${mod_id}";
    public static final String MOD_NAME = "${mod_name}";
    public static final String PREVIOUS_MOD_ID = "${previous_mod_id}";
    public static final String LEGACY_MOD_ID = "${legacy_mod_id}";
    public static final String CONFIG_FILE = MOD_ID + ".json";
    public static final String CONFIG_TRANSLATION_PREFIX = MOD_ID + ".config.";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private ModIdentity() {}

    public static boolean disabledBySystemProperty() {
        return Boolean.getBoolean(MOD_ID + ".disabled")
                || Boolean.getBoolean(PREVIOUS_MOD_ID + ".disabled")
                || Boolean.getBoolean(LEGACY_MOD_ID + ".disabled");
    }
}
