package dev.evasex.ewaira.text;

import dev.evasex.ewaira.ModIdentity;
import java.util.concurrent.atomic.AtomicLong;

public final class TextOptimization {
    private static final AtomicLong FONT_GENERATION = new AtomicLong();
    private static volatile boolean enabled = !ModIdentity.disabledBySystemProperty();

    private TextOptimization() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        invalidateFonts();
    }

    public static long fontGeneration() {
        return FONT_GENERATION.get();
    }

    public static void invalidateFonts() {
        FONT_GENERATION.incrementAndGet();
    }
}
