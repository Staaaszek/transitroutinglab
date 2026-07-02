package pl.edu.pitp.transit.engine;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RoutingEngineRegistry {
    private final Map<String, RoutingEngine> engines;

    public RoutingEngineRegistry(List<RoutingEngine> engines) {
        this.engines = engines.stream().collect(Collectors.toUnmodifiableMap(RoutingEngine::id, Function.identity()));
    }

    public List<RoutingEngine> all() {
        return engines.values().stream()
                .sorted(Comparator.comparing(RoutingEngine::displayName))
                .toList();
    }

    public RoutingEngine get(String id) {
        RoutingEngine engine = engines.get(id);
        if (engine == null) {
            throw new IllegalArgumentException("Unknown routing engine: " + id);
        }
        return engine;
    }
}
