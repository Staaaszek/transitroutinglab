package pl.edu.pitp.transit.model;

import java.util.List;

public record TransitTrip(
        int id,
        String feedId,
        String externalId,
        int routeId,
        String routeShortName,
        String headsign,
        String serviceId,
        List<Integer> segmentIds
) {
}
