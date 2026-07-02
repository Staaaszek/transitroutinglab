package pl.edu.pitp.transit.api;

import pl.edu.pitp.transit.engine.EngineParameter;
import pl.edu.pitp.transit.engine.SearchDiagnostics;
import pl.edu.pitp.transit.model.GeoPoint;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ApiDtos {
    private ApiDtos() {
    }

    public record FeedDto(String id, String name, int stopCount, int segmentCount, int tripCount) {
    }

    public record EngineDto(String id, String displayName, List<EngineParameter> parameters) {
    }

    public record StopDto(int id, String name, String code, String platformCode, double lat, double lon) {
    }

    public record DepartureDto(String routeShortName, String headsign, int tripId, int toStopId, String toStopName, int departureSeconds) {
    }

    public record RoutingRequest(
            String feedId,
            String engineId,
            int fromStopId,
            int toStopId,
            LocalDateTime dateTime,
            Integer maxResults,
            Map<String, Object> parameters
    ) {
    }

    public record RoutingResponse(String feedId, String engineId, List<RouteDto> routes, SearchDiagnostics diagnostics) {
    }

    public record RouteDto(String id, RouteSummaryDto summary, List<RouteLegDto> legs, List<GeoPoint> geometry) {
    }

    public record RouteSummaryDto(String title, int departureSeconds, int arrivalSeconds, int durationSeconds, int transfers) {
    }

    public record RouteLegDto(
            String type,
            Integer tripId,
            String routeShortName,
            String headsign,
            int fromStopId,
            String fromStopName,
            int toStopId,
            String toStopName,
            int departureSeconds,
            int arrivalSeconds,
            List<GeoPoint> geometry
    ) {
    }

    public record TripStopDto(int stopId, String stopName, int arrivalSeconds, int departureSeconds, int sequence, boolean inLeg) {
    }

    public record TripStopsDto(int tripId, String routeShortName, String headsign, List<TripStopDto> stops) {
    }
}
