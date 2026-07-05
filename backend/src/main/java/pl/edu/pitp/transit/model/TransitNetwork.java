package pl.edu.pitp.transit.model;

import java.util.List;
import java.util.Map;

public record TransitNetwork(
        String feedId,
        List<TransitStop> stops,
        List<TripSegment> segments,
        List<TransitTrip> trips,
        List<List<Integer>> outgoingSegmentsByStop,
        List<List<Integer>> segmentsByTrip,
        List<List<PlatformTransfer>> platformTransfersByStop,
        Map<String, Integer> stopExternalIds
) {
    public TransitStop stop(int stopId) {
        return stops.get(stopId);
    }

    public TripSegment segment(int segmentId) {
        return segments.get(segmentId);
    }

    public TransitTrip trip(int tripId) {
        return trips.get(tripId);
    }

    public List<Integer> departuresFrom(int stopId) {
        return outgoingSegmentsByStop.get(stopId);
    }

    public List<Integer> tripSegments(int tripId) {
        return segmentsByTrip.get(tripId);
    }

    public List<PlatformTransfer> platformTransfersFrom(int stopId) {
        return platformTransfersByStop.get(stopId);
    }
}
