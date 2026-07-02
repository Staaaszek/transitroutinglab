package pl.edu.pitp.transit.engine;

import java.time.LocalDateTime;
import java.util.Map;

public record RoutingQuery(
        int fromStopId,
        int toStopId,
        LocalDateTime dateTime,
        int maxResults,
        Map<String, Object> parameters
) {
}
