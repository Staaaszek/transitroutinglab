package pl.edu.pitp.transit.service;

import org.springframework.stereotype.Service;
import pl.edu.pitp.transit.api.ApiDtos;
import pl.edu.pitp.transit.api.RoutingResponseMapper;
import pl.edu.pitp.transit.engine.RoutingEngine;
import pl.edu.pitp.transit.engine.RoutingEngineRegistry;
import pl.edu.pitp.transit.engine.RoutingQuery;
import pl.edu.pitp.transit.feed.FeedCatalog;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class RoutingService {
    private final FeedCatalog feedCatalog;
    private final RoutingEngineRegistry engineRegistry;
    private final RoutingResponseMapper mapper;

    public RoutingService(FeedCatalog feedCatalog, RoutingEngineRegistry engineRegistry, RoutingResponseMapper mapper) {
        this.feedCatalog = feedCatalog;
        this.engineRegistry = engineRegistry;
        this.mapper = mapper;
    }

    public ApiDtos.RoutingResponse search(ApiDtos.RoutingRequest request) {
        FeedCatalog.LoadedFeed feed = feedCatalog.get(request.feedId());
        RoutingEngine engine = engineRegistry.get(request.engineId());
        RoutingQuery query = new RoutingQuery(
                request.fromStopId(),
                request.toStopId(),
                request.dateTime() == null ? LocalDateTime.now() : request.dateTime(),
                request.maxResults() == null ? 5 : Math.max(1, Math.min(20, request.maxResults())),
                request.parameters() == null ? Map.of() : request.parameters()
        );
        return mapper.toResponse(feed.id(), engine.id(), engine.findRoutes(query, feed.network()), feed.network());
    }
}
