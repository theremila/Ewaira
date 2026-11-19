package dev.evasex.ewaira.text;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextWidthCacheTest {
    @Test void keepsExactFractionalWidthsAndEqualStrings() {
        TextWidthCache cache = new TextWidthCache();
        assertTrue(Float.isNaN(cache.get("Hello", 0)));
        cache.put("Hello", 12.75f);
        assertEquals(12.75f, cache.get(new String("Hello"), 0));
    }

    @Test void collisionCannotReturnAnotherStringsWidth() {
        TextWidthCache cache = new TextWidthCache();
        assertEquals("AaAa".hashCode(), "BBBB".hashCode());
        cache.get("AaAa", 0);
        cache.put("AaAa", 11);
        assertTrue(Float.isNaN(cache.get("BBBB", 0)));
        cache.put("BBBB", 23);
        assertTrue(Float.isNaN(cache.get("AaAa", 0)));
        assertEquals(23, cache.get("BBBB", 0));
    }

    @Test void invalidatesOnFontGenerationChange() {
        TextWidthCache cache = new TextWidthCache();
        cache.get("Resource pack", 7);
        cache.put("Resource pack", 22);
        assertTrue(Float.isNaN(cache.get("Resource pack", 8)));
        cache.put("Resource pack", 33);
        assertEquals(33, cache.get("Resource pack", 8));
    }

    @Test void boundsRetainedStringsAndSkipsOtherThreads() throws InterruptedException {
        TextWidthCache cache = new TextWidthCache();
        assertFalse(cache.accepts(null));
        assertFalse(cache.accepts("abc"));
        assertTrue(cache.accepts("a".repeat(256)));
        assertFalse(cache.accepts("a".repeat(257)));
        boolean[] accepted = {true};
        Thread worker = new Thread(() -> accepted[0] = cache.accepts("worker"));
        worker.start();
        worker.join();
        assertFalse(accepted[0]);
    }

    @Test void preservesUnicodeFormattingAndZeroWidths() {
        TextWidthCache cache = new TextWidthCache();
        for (String text : new String[]{"Привет", "你好世界", "§lBold§r", "a😀bc", "\u200b\u200b\u200b\u200b"}) {
            cache.get(text, 0);
            cache.put(text, 0);
            assertEquals(0, cache.get(text, 0));
        }
    }

    @Test void toggleInvalidatesPreviouslyMeasuredWidths() {
        long generation = TextOptimization.fontGeneration();
        TextOptimization.setEnabled(false);
        assertFalse(TextOptimization.isEnabled());
        assertTrue(TextOptimization.fontGeneration() > generation);
        TextOptimization.setEnabled(true);
        assertTrue(TextOptimization.isEnabled());
    }
}
