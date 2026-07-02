package pl.edu.pitp.transit.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.edu.pitp.transit.feed.FeedCatalog;
import pl.edu.pitp.transit.model.TransitNetwork;
import pl.edu.pitp.transit.model.TransitTrip;
import pl.edu.pitp.transit.model.TripSegment;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/trips")
public class TripController {
    private final FeedCatalog feedCatalog;

    public TripController(FeedCatalog feedCatalog) {
        this.feedCatalog = feedCatalog;
    }

    @GetMapping("/{tripId}/stops")
    public ApiDtos.TripStopsDto tripStops(
            @PathVariable int tripId,
            @RequestParam(required = false) String feedId,
            @RequestParam int fromStopId,
            @RequestParam int toStopId
    ) {
        TransitNetwork network = feedCatalog.get(feedId).network();
        TransitTrip trip = network.trip(tripId);
        List<Integer> segmentIds = network.tripSegments(tripId);
        List<ApiDtos.TripStopDto> stops = new ArrayList<>();
        boolean inLeg = false;
        int sequence = 1;
        for (int i = 0; i < segmentIds.size(); i++) {
            TripSegment segment = network.segment(segmentIds.get(i));
            if (i == 0) {
                stops.add(stopDto(network, segment.fromStopId(), segment.departureSeconds(), segment.departureSeconds(), sequence++, segment.fromStopId() == fromStopId));
            }
            if (segment.fromStopId() == fromStopId) {
                inLeg = true;
            }
            stops.add(stopDto(network, segment.toStopId(), segment.arrivalSeconds(), segment.arrivalSeconds(), sequence++, inLeg));
            if (segment.toStopId() == toStopId) {
                inLeg = false;
            }
        }
        return new ApiDtos.TripStopsDto(trip.id(), trip.routeShortName(), trip.headsign(), stops);
    }

    private ApiDtos.TripStopDto stopDto(TransitNetwork network, int stopId, int arrival, int departure, int sequence, boolean inLeg) {
        return new ApiDtos.TripStopDto(stopId, network.stop(stopId).name(), arrival, departure, sequence, inLeg);
    }
}
