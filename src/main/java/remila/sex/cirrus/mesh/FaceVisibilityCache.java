package remila.sex.cirrus.mesh;

/** Thread-confined identity cache; identity keys are immutable block states or face shapes, never worlds. */
public final class FaceVisibilityCache {
    public static final int MISS = 2;
    private static final int CAPACITY = 4096;
    private final Object[] states = new Object[CAPACITY];
    private final Object[] neighbors = new Object[CAPACITY];
    private final byte[] directions = new byte[CAPACITY];
    private final byte[] values = new byte[CAPACITY];

    public int get(Object state, Object neighbor, int direction) {
        int slot = slot(state, neighbor, direction);
        return state != null && states[slot] == state && neighbors[slot] == neighbor && directions[slot] == direction
                ? values[slot] : MISS;
    }
    public void put(Object state, Object neighbor, int direction, boolean visible) {
        int slot = slot(state, neighbor, direction);
        states[slot] = state;
        neighbors[slot] = neighbor;
        directions[slot] = (byte) direction;
        values[slot] = (byte) (visible ? 1 : 0);
    }
    private static int slot(Object state, Object neighbor, int direction) {
        int hash = (System.identityHashCode(state) * 31 + System.identityHashCode(neighbor)) * 31 + direction;
        return (hash ^ (hash >>> 16)) & (CAPACITY - 1);
    }
}
