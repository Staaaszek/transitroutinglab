package pl.edu.pitp.transit.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.edu.pitp.transit.feed.FeedCatalog;
import pl.edu.pitp.transit.model.TransitNetwork;
import pl.edu.pitp.transit.model.TransitStop;
import pl.edu.pitp.transit.model.TransitTrip;
import pl.edu.pitp.transit.model.TripSegment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/stops")
public class StopController {
    private final FeedCatalog feedCatalog;

    public StopController(FeedCatalog feedCatalog) {
        this.feedCatalog = feedCatalog;
    }

    @GetMapping
    public List<ApiDtos.StopDto> stops(
            @RequestParam(required = false) String feedId,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "2500") int limit
    ) {
        TransitNetwork network = feedCatalog.get(feedId).network();
        String normalized = normalize(query);
        return network.stops().stream()
                .filter(stop -> normalized.isBlank() || normalize(stop.name()).contains(normalized) || stop.code().contains(query))
                .limit(Math.max(1, Math.min(limit, 5000)))
                .map(this::stopDto)
                .toList();
    }

    @GetMapping("/{stopId}/departures")
    public List<ApiDtos.DepartureDto> departures(
            @PathVariable int stopId,
            @RequestParam(required = false) String feedId,
            @RequestParam(required = false) LocalDateTime dateTime,
            @RequestParam(defaultValue = "12") int limit
    ) {
        TransitNetwork network = feedCatalog.get(feedId).network();
        LocalDateTime effective = dateTime == null ? LocalDateTime.now() : dateTime;
        LocalDate date = effective.toLocalDate();
        int seconds = effective.toLocalTime().toSecondOfDay();
        return network.departuresFrom(stopId).stream()
                .map(network::segment)
                .filter(segment -> segment.departureSeconds() >= seconds)
                .filter(segment -> network.serviceActive(segment.tripId(), date))
                .sorted(Comparator.comparingInt(TripSegment::departureSeconds))
                .limit(Math.max(1, Math.min(limit, 50)))
                .map(segment -> departureDto(segment, network))
                .toList();
    }

    private ApiDtos.DepartureDto departureDto(TripSegment segment, TransitNetwork network) {
        TransitTrip trip = network.trip(segment.tripId());
        TransitStop to = network.stop(segment.toStopId());
        return new ApiDtos.DepartureDto(
                trip.routeShortName(),
                trip.headsign(),
                trip.id(),
                to.id(),
                to.name(),
                segment.departureSeconds()
        );
    }

    private ApiDtos.StopDto stopDto(TransitStop stop) {
        return new ApiDtos.StopDto(stop.id(), stop.name(), stop.code(), stop.platformCode(), stop.lat(), stop.lon());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
