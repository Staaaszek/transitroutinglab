package pl.edu.pitp.transit.engine;

import org.springframework.stereotype.Component;
import pl.edu.pitp.transit.model.PlatformTransfer;
import pl.edu.pitp.transit.model.TransitNetwork;
import pl.edu.pitp.transit.model.TripSegment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DirectTripEngine implements RoutingEngine {
    @Override
    public String id() {
        return "direct-trip";
    }

    @Override
    public String displayName() {
        return "Direct trip baseline";
    }

    @Override
    public List<EngineParameter> parameters() {
        return List.of(
                EngineParameter.number("scanDepartures", "Departures scanned per start platform", 400),
                EngineParameter.bool("usePlatformClique", "Use platform clique", true)
        );
    }

    @Override
    public RoutePlan findRoutes(RoutingQuery query, TransitNetwork network) {
        long start = System.nanoTime();
        int startSeconds = query.dateTime().toLocalTime().toSecondOfDay();
        LocalDate date = query.dateTime().toLocalDate();
        int scanDepartures = intParam(query.parameters(), "scanDepartures", 400);
        boolean useClique = boolParam(query.parameters(), "usePlatformClique", true);

        List<Integer> sourceStops = expandPlatformClique(network, query.fromStopId(), useClique);
        Set<Integer> targetStops = new HashSet<>(expandPlatformClique(network, query.toStopId(), useClique));
        List<RouteVariant> variants = new ArrayList<>();
        long visited = 0;

        for (int sourceStop : sourceStops) {
            List<Integer> departures = network.departuresFrom(sourceStop);
            int scanned = 0;
            for (int segmentId : departures) {
                TripSegment first = network.segment(segmentId);
                if (first.departureSeconds() < startSeconds || !network.serviceActive(first.tripId(), date)) {
                    continue;
                }
                visited++;
                scanned++;
                if (scanned > scanDepartures) {
                    break;
                }
                RouteVariant route = findTargetOnTrip(network, query.fromStopId(), query.toStopId(), sourceStop, first, targetStops, startSeconds, useClique, variants.size());
                if (route != null) {
                    variants.add(route);
                    if (variants.size() >= query.maxResults()) {
                        return plan(variants, visited, start, "Found direct trip candidates.");
                    }
                }
            }
        }

        String message = variants.isEmpty()
                ? "No direct trip found. This engine is a baseline stub for UI and API validation."
                : "Found direct trip candidates.";
        return plan(variants, visited, start, message);
    }

    private RouteVariant findTargetOnTrip(
            TransitNetwork network,
            int requestedFromStop,
            int requestedToStop,
            int actualFromStop,
            TripSegment first,
            Set<Integer> targetStops,
            int startSeconds,
            boolean useClique,
            int index
    ) {
        List<Integer> tripSegments = network.tripSegments(first.tripId());
        int startIndex = tripSegments.indexOf(first.id());
        if (startIndex < 0) {
            return null;
        }

        List<Integer> rideSegments = new ArrayList<>();
        for (int i = startIndex; i < tripSegments.size(); i++) {
            TripSegment segment = network.segment(tripSegments.get(i));
            rideSegments.add(segment.id());
            if (targetStops.contains(segment.toStopId())) {
                List<PlanLeg> legs = new ArrayList<>();
                if (useClique && requestedFromStop != actualFromStop) {
                    legs.add(PlanLeg.transfer(requestedFromStop, actualFromStop, startSeconds));
                }
                legs.add(new PlanLeg(
                        LegType.RIDE,
                        first.tripId(),
                        actualFromStop,
                        segment.toStopId(),
                        first.departureSeconds(),
                        segment.arrivalSeconds(),
                        List.copyOf(rideSegments)
                ));
                if (useClique && segment.toStopId() != requestedToStop) {
                    legs.add(PlanLeg.transfer(segment.toStopId(), requestedToStop, segment.arrivalSeconds()));
                }
                return new RouteVariant("direct-" + index, List.copyOf(legs));
            }
        }
        return null;
    }

    private List<Integer> expandPlatformClique(TransitNetwork network, int stopId, boolean enabled) {
        if (!enabled) {
            return List.of(stopId);
        }
        List<Integer> stops = new ArrayList<>();
        stops.add(stopId);
        for (PlatformTransfer transfer : network.platformTransfersFrom(stopId)) {
            stops.add(transfer.toStopId());
        }
        return stops.stream().distinct().sorted().toList();
    }

    private RoutePlan plan(List<RouteVariant> variants, long visited, long start, String message) {
        List<RouteVariant> ordered = variants.stream()
                .sorted(Comparator.comparingInt(route -> route.legs().get(route.legs().size() - 1).arrivalSeconds()))
                .toList();
        return new RoutePlan(ordered, new SearchDiagnostics(message, visited, (System.nanoTime() - start) / 1_000_000));
    }

    private int intParam(Map<String, Object> params, String name, int fallback) {
        Object value = params.get(name);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Integer.parseInt(string);
        }
        return fallback;
    }

    private boolean boolParam(Map<String, Object> params, String name, boolean fallback) {
        Object value = params.get(name);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string && !string.isBlank()) {
            return Boolean.parseBoolean(string);
        }
        return fallback;
    }
}
