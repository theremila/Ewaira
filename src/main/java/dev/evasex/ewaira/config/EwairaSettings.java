package dev.evasex.ewaira.config;

import java.util.EnumMap;
import java.util.Map;

/** User choices only; rendering distances are derived from the current view. */
public record EwairaSettings(boolean enabled, boolean debug, Map<Optimization, Boolean> modules) {
    public static final double DEFAULT_MARGIN = 1.0 / 16;
    public EwairaSettings {
        EnumMap<Optimization, Boolean> complete = new EnumMap<>(Optimization.class);
        for (Optimization module : Optimization.values()) complete.put(module, modules.getOrDefault(module, true));
        modules = Map.copyOf(complete);
    }
    public static EwairaSettings defaults() { return new EwairaSettings(true, false, Map.of()); }
    public boolean active(Optimization module) { return enabled && modules.get(module); }
    public EwairaSettings withEnabled(boolean value) { return new EwairaSettings(value, debug, modules); }
    public EwairaSettings withDebug(boolean value) { return new EwairaSettings(enabled, value, modules); }
    public EwairaSettings withModule(Optimization module, boolean value) {
        EnumMap<Optimization, Boolean> copy = new EnumMap<>(Optimization.class);
        copy.putAll(modules); copy.put(module, value);
        return new EwairaSettings(enabled, debug, copy);
    }
}
