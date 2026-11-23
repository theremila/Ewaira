package remila.sex.cirrus.text;

import java.util.Arrays;

/** A bounded, allocation-free hit path. Collisions replace entries, never reuse a wrong width. */
public final class TextWidthCache {
    private static final int CAPACITY = 1024;
    private static final int MAX_LENGTH = 256;
    private final Thread owner = Thread.currentThread();
    private final String[] keys = new String[CAPACITY];
    private final float[] widths = new float[CAPACITY];
    private long generation = -1;
    private long hits;
    private long misses;

    public boolean accepts(String text) {
        return Thread.currentThread() == owner && text != null
                && text.length() >= 4 && text.length() <= MAX_LENGTH;
    }

    public float get(String text, long currentGeneration) {
        if (generation != currentGeneration) {
            Arrays.fill(keys, null);
            generation = currentGeneration;
        }
        int slot = slot(text);
        if (text.equals(keys[slot])) {
            hits++;
            return widths[slot];
        }
        misses++;
        return Float.NaN;
    }

    public void put(String text, float width) {
        int slot = slot(text);
        keys[slot] = text;
        widths[slot] = width;
    }

    public String statistics() {
        return "hits=" + hits + ", misses=" + misses;
    }

    private static int slot(String text) {
        int hash = text.hashCode();
        return (hash ^ (hash >>> 16)) & (CAPACITY - 1);
    }
}
