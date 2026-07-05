package pl.edu.pitp.transit.engine;

import java.util.Map;

public record RoutingQuery(
        int fromStopId,
        int toStopId,
        int departureSeconds,
        int maxResults,
        Map<String, Object> parameters
) {
}
