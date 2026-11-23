package remila.sex.cirrus.config;

import java.util.EnumMap;
import java.util.Map;

/** User choices only; rendering distances are derived from the current view. */
public record CirrusSettings(boolean enabled, boolean debug, Map<Optimization, Boolean> modules) {
    public static final double DEFAULT_MARGIN = 1.0 / 16;
    public CirrusSettings {
        EnumMap<Optimization, Boolean> complete = new EnumMap<>(Optimization.class);
        for (Optimization module : Optimization.values()) complete.put(module, modules.getOrDefault(module, true));
        modules = Map.copyOf(complete);
    }
    public static CirrusSettings defaults() { return new CirrusSettings(true, false, Map.of()); }
    public boolean active(Optimization module) { return enabled && modules.get(module); }
    public CirrusSettings withEnabled(boolean value) { return new CirrusSettings(value, debug, modules); }
    public CirrusSettings withDebug(boolean value) { return new CirrusSettings(enabled, value, modules); }
    public CirrusSettings withModule(Optimization module, boolean value) {
        EnumMap<Optimization, Boolean> copy = new EnumMap<>(Optimization.class);
        copy.putAll(modules); copy.put(module, value);
        return new CirrusSettings(enabled, debug, copy);
    }
}
