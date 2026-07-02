package pl.edu.pitp.transit.model;

public record TripSegment(
        int id,
        int tripId,
        int routeId,
        String routeShortName,
        int fromStopId,
        int toStopId,
        int departureSeconds,
        int arrivalSeconds,
        int sequence
) {
}
