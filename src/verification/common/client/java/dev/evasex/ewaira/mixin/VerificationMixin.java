package dev.evasex.ewaira.mixin;

import dev.evasex.ewaira.text.FontWidthCacheAccess;
import dev.evasex.ewaira.text.TextOptimization;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Included only by -PverifyClient; never part of release builds. */
@Mixin(Minecraft.class)
abstract class VerificationMixin {
    @Unique private static final Logger EWAIRA_LOG = LoggerFactory.getLogger("EwairaVerify");
    @Unique private final long ewaira$started = System.nanoTime();
    @Unique private CompletableFuture<Void> ewaira$reload;
    @Unique private long ewaira$generationBeforeReload;
    @Unique private boolean ewaira$finished;
    @Unique private static volatile int ewaira$sink;

    @Inject(method = "runTick", at = @At("TAIL"))
    private void ewaira$verify(CallbackInfo ci) throws IOException, ClassNotFoundException {
        if (ewaira$finished || System.nanoTime() - ewaira$started < 25_000_000_000L) {
            return;
        }
        Minecraft client = (Minecraft) (Object) this;
        if (ewaira$reload == null) {
            ewaira$checkWidths(client.font);
            ewaira$generationBeforeReload = TextOptimization.fontGeneration();
            ewaira$reload = client.reloadResourcePacks();
            return;
        }
        if (!ewaira$reload.isDone()) {
            return;
        }
        ewaira$reload.join();
        if (TextOptimization.fontGeneration() <= ewaira$generationBeforeReload) {
            throw new AssertionError("Font reload did not invalidate widths");
        }
        ewaira$checkWidths(client.font);
        String[] corpus = ewaira$corpus();
        long[] baseline = new long[7];
        long[] cached = new long[7];
        for (int round = 0; round < 10; round++) {
            ewaira$measure(client.font, corpus, false);
            ewaira$measure(client.font, corpus, true);
        }
        for (int round = 0; round < baseline.length; round++) {
            // Alternate order to reduce warm-up/order bias.
            if (round % 2 == 0) {
                baseline[round] = ewaira$measure(client.font, corpus, false);
                cached[round] = ewaira$measure(client.font, corpus, true);
            } else {
                cached[round] = ewaira$measure(client.font, corpus, true);
                baseline[round] = ewaira$measure(client.font, corpus, false);
            }
        }
        Arrays.sort(baseline);
        Arrays.sort(cached);
        String report = "PASS: transformed Font and FontSet; "
                + "width equivalence, toggle, resource reload.\n"
                + "Repeated-string microbenchmark, 100000 queries; not gameplay FPS.\n"
                + "baseline_median_ns=" + baseline[3] + "\ncache_median_ns=" + cached[3]
                + "\n" + ((FontWidthCacheAccess) client.font).ewaira$getWidthCache().statistics() + "\n";
        Files.writeString(Path.of("ewaira-verification.txt"), report);
        EWAIRA_LOG.info(report);
        ewaira$finished = true;
        client.stop();
    }

    @Unique
    private static void ewaira$checkWidths(Font font) {
        String[] texts = {"", "abc", "Hello, Minecraft!", "Привет, Minecraft!", "你好世界",
                "مرحبا بالعالم", "§lBold§r Normal", "a😀bc", "\u200b\u200b\u200b\u200b",
                "a".repeat(257), "AaAa", "BBBB"};
        for (String text : texts) {
            TextOptimization.setEnabled(false);
            int expected = font.width(text);
            TextOptimization.setEnabled(true);
            for (int repetition = 0; repetition < 3; repetition++) {
                int actual = font.width(new String(text));
                if (actual != expected) {
                    throw new AssertionError("Wrong width for " + text + ": " + actual + " != " + expected);
                }
            }
        }
        String statistics = ((FontWidthCacheAccess) font).ewaira$getWidthCache().statistics();
        if (statistics.startsWith("hits=0,")) {
            throw new AssertionError("No actual cache hits: " + statistics);
        }
    }

    @Unique
    private static String[] ewaira$corpus() {
        String[] texts = new String[64];
        for (int i = 0; i < texts.length; i++) {
            texts[i] = "Minecraft inventory item " + i + " — §lFormatted§r text";
        }
        return texts;
    }

    @Unique
    private static long ewaira$measure(Font font, String[] texts, boolean enabled) {
        TextOptimization.setEnabled(enabled);
        for (String text : texts) {
            font.width(text);
        }
        long start = System.nanoTime();
        int sum = 0;
        for (int i = 0; i < 100_000; i++) {
            sum += font.width(texts[i & 63]);
        }
        ewaira$sink = sum;
        return System.nanoTime() - start;
    }
}
