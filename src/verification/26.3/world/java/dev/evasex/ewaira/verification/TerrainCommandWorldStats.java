package dev.evasex.ewaira.verification;

/** The world fixture renders and samples on the client thread. */
public final class TerrainCommandWorldStats {
    private static long reusedBytes;

    private TerrainCommandWorldStats() {}

    public static void reuse(long bytes) { reusedBytes += bytes; }
    public static long reusedBytes() { return reusedBytes; }
}
