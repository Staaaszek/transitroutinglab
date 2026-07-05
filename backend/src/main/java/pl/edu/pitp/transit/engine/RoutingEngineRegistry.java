package pl.edu.pitp.transit.engine;

import org.springframework.stereotype.Component;
import pl.edu.pitp.transit.feed.FeedCatalog;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RoutingEngineRegistry {
    private final Map<String, RoutingEngineFactory> factories;
    private final Map<String, Map<String, RoutingEngine>> enginesByFeed = new ConcurrentHashMap<>();

    public RoutingEngineRegistry(List<RoutingEngineFactory> factories) {
        this.factories = factories.stream().collect(Collectors.toUnmodifiableMap(RoutingEngineFactory::id, Function.identity()));
    }

    public List<RoutingEngineFactory> all() {
        return factories.values().stream()
                .sorted(Comparator.comparing(RoutingEngineFactory::displayName))
                .toList();
    }

    public RoutingEngine get(FeedCatalog.LoadedFeed feed, String id) {
        Map<String, RoutingEngine> engines = enginesByFeed.computeIfAbsent(feed.id(), ignored -> createEngines(feed));
        RoutingEngine engine = engines.get(id);
        if (engine == null) {
            throw new IllegalArgumentException("Unknown routing engine: " + id);
        }
        return engine;
    }

    private Map<String, RoutingEngine> createEngines(FeedCatalog.LoadedFeed feed) {
        return factories.values().stream()
                .map(factory -> factory.create(feed.network()))
                .collect(Collectors.toUnmodifiableMap(RoutingEngine::id, Function.identity()));
    }
}
