package pl.edu.pitp.transit.engine;

import java.util.List;

public record PlanLeg(
        LegType type,
        Integer tripId,
        int fromStopId,
        int toStopId,
        int departureSeconds,
        int arrivalSeconds,
        List<Integer> segmentIds
) {
    public static PlanLeg transfer(int fromStopId, int toStopId, int timeSeconds) {
        return new PlanLeg(LegType.PLATFORM_TRANSFER, null, fromStopId, toStopId, timeSeconds, timeSeconds, List.of());
    }
}
