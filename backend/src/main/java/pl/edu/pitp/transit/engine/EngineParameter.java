package pl.edu.pitp.transit.engine;

import java.util.List;

public record EngineParameter(
        String name,
        String label,
        String type,
        Object defaultValue,
        List<Object> options
) {
    public static EngineParameter number(String name, String label, Number defaultValue) {
        return new EngineParameter(name, label, "number", defaultValue, List.of());
    }

    public static EngineParameter bool(String name, String label, boolean defaultValue) {
        return new EngineParameter(name, label, "boolean", defaultValue, List.of());
    }
}
