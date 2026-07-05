package pl.edu.pitp.transit.engine;

import pl.edu.pitp.transit.model.PlatformTransfer;
import pl.edu.pitp.transit.model.TransitNetwork;
import pl.edu.pitp.transit.model.TransitTrip;
import pl.edu.pitp.transit.model.TripSegment;

import java.util.*;

public class RaptorEngine implements RoutingEngine {

    private final TransitNetwork network;
    private final List<RaptorRoute> routes;
    private final int[] routeStops;
    private final RaptorStopTime[] stopTimes;
    private final RaptorStop[] stops;
    private final int[] stopRoutes;
    private final int[] stopRouteIndexes;
    private final int[] transfers;
    private final int[] routeTrips;

    public RaptorEngine(TransitNetwork network) {
        this.network = network;
        RaptorTables tables = buildTables(network);
        this.routes = tables.routes();
        this.routeStops = tables.routeStops();
        this.stopTimes = tables.stopTimes();
        this.stops = tables.stops();
        this.stopRoutes = tables.stopRoutes();
        this.stopRouteIndexes = tables.stopRouteIndexes();
        this.transfers = tables.transfers();
        this.routeTrips = tables.routeTrips();
    }

    @Override
    public String id() {
        return "raptor-engine";
    }

    @Override
    public String displayName() {
        return "RAPTOR";
    }

    @Override
    public List<EngineParameter> parameters() {
        return List.of(
                EngineParameter.number("maxTransfers", "Max transfers", 4),
                EngineParameter.number("samePlatformTransitTime", "Same platform transit time", 0),
                EngineParameter.number("differentPlatformChangeTime", "Different platform change time", 120)
        );
    }

    @Override
    public RoutePlan findRoutes(RoutingQuery query) {
        int maxTransfers = intParam(query.parameters(), "maxTransfers", 4);
        int samePlatformTransitTime = intParam(query.parameters(), "samePlatformTransitTime", 10);
        int differentPlatformChangeTime = intParam(query.parameters(), "differentPlatformChangeTime", 60);
        int rounds = maxTransfers + 1;
        int [] bestArrivalTime = new int[stops.length];
        Arrays.fill(bestArrivalTime, Integer.MAX_VALUE);
        int [][] bestArrivalTimeInRound = new int[rounds + 1][stops.length];
        RaptorParent [][] parents = new RaptorParent[rounds + 1][stops.length];
        Arrays.fill(bestArrivalTimeInRound[0], Integer.MAX_VALUE);

        bestArrivalTime[query.fromStopId()] = query.departureSeconds();
        bestArrivalTimeInRound[0][query.fromStopId()] = query.departureSeconds();


        Set<Integer> markedStops = new HashSet<>();
        Set<Integer> routesToProcess = new HashSet<>();
        markedStops.add(query.fromStopId());
        int [] firstMarkedStopRouteId = new int[routes.size()];

        for(int k = 1; k <= rounds; k++){
            Arrays.fill(bestArrivalTimeInRound[k], Integer.MAX_VALUE);
            Arrays.fill(firstMarkedStopRouteId, Integer.MAX_VALUE);

            //Prepare routes to scan
            routesToProcess.clear();
            Iterator<Integer> iter = markedStops.iterator();
            while (iter.hasNext()){
                int stopId = iter.next();
                RaptorStop raptorStop = stops[stopId];
                for (int entry = raptorStop.stopRoutesStart(); entry < raptorStop.stopRoutesEnd(); entry++) {
                    int routeId = stopRoutes[entry];
                    int stopRouteId = stopRouteIndexes[entry];
                    if (!routesToProcess.contains(routeId) || stopRouteId <= firstMarkedStopRouteId[routeId]) {
                        firstMarkedStopRouteId[routeId] = stopRouteId;
                        routesToProcess.add(routeId);
                    }
                }
                iter.remove();
            }


            //Scan routes
            for(int routeId : routesToProcess){
                RaptorRoute route = routes.get(routeId);
                int currentTripId = route.trips();
                int currentBoardStopId = -1;
                int currentBoardStopRouteId = -1;
                for(int stopRouteId = firstMarkedStopRouteId[routeId]; stopRouteId < route.stops(); stopRouteId++){
                    int stopId = routeStops[route.routeStopsStart() + stopRouteId];
                    RaptorStopTime currentTripStopTime = null;
                    if(currentTripId != route.trips()){
                        int stopTimeIndex = route.stopTimesStart() + currentTripId*route.stops() + stopRouteId;
                        currentTripStopTime = stopTimes[stopTimeIndex];
                    }

                    if(currentTripStopTime != null && currentTripStopTime.arrivalSeconds() < Math.min(bestArrivalTime[stopId], bestArrivalTime[query.toStopId()])){
                        bestArrivalTime[stopId] = currentTripStopTime.arrivalSeconds();
                        bestArrivalTimeInRound[k][stopId] = currentTripStopTime.arrivalSeconds();
                        parents[k][stopId] = RaptorParent.ride(currentBoardStopId, routeId, currentTripId, currentBoardStopRouteId, stopRouteId);
                        markedStops.add(stopId);
                    }

                    int earliestPossibleDeparture = bestArrivalTimeInRound[k - 1][stopId] == Integer.MAX_VALUE ? Integer.MAX_VALUE : bestArrivalTimeInRound[k - 1][stopId] + samePlatformTransitTime;

                    if(currentTripStopTime == null || earliestPossibleDeparture <= currentTripStopTime.departureSeconds()){
                        int previousTripId = currentTripId;
                        while (currentTripId > 0 && stopTimes[route.stopTimesStart() + (currentTripId - 1)*route.stops() + stopRouteId].departureSeconds() >= earliestPossibleDeparture){
                            currentTripId--;
                        }
                        if (currentTripId != previousTripId) {
                            currentBoardStopId = stopId;
                            currentBoardStopRouteId = stopRouteId;
                        }
                    }
                }
            }

            //Apply foot transfers
            for(Integer stopId : List.copyOf(markedStops)){
                for(int entry = stops[stopId].transfersStart(); entry < stops[stopId].transfersEnd(); entry++){
                    int otherStopId = transfers[entry];
                    if(bestArrivalTime[stopId] + differentPlatformChangeTime < Math.min(bestArrivalTime[otherStopId], bestArrivalTime[query.toStopId()])){
                        bestArrivalTimeInRound[k][otherStopId] = bestArrivalTime[stopId] + differentPlatformChangeTime;
                        bestArrivalTime[otherStopId] = bestArrivalTime[stopId] + differentPlatformChangeTime;
                        parents[k][otherStopId] = RaptorParent.transfer(stopId, bestArrivalTime[stopId], bestArrivalTime[otherStopId]);
                        markedStops.add(otherStopId);
                    }
                }
            }

            if(markedStops.isEmpty()) break;
        }

        List<RouteVariant> variants = buildRouteVariants(query, bestArrivalTime, bestArrivalTimeInRound, parents);

        return new RoutePlan(
                variants,
                new SearchDiagnostics(
                        "RAPTOR model ready. maxTransfers=%d samePlatformTransitTime=%d differentPlatformChangeTime=%d stops=%d routes=%d routeStops=%d stopTimes=%d trips=%d."
                                .formatted(maxTransfers, samePlatformTransitTime, differentPlatformChangeTime, stops.length, routes.size(), routeStops.length, stopTimes.length, routeTrips.length),
                        0,
                        0
                )
        );
    }

    private List<RouteVariant> buildRouteVariants(
            RoutingQuery query,
            int[] bestArrivalTime,
            int[][] bestArrivalTimeInRound,
            RaptorParent[][] parents
    ) {
        if (bestArrivalTime[query.toStopId()] == Integer.MAX_VALUE) {
            return List.of();
        }
        int bestRound = -1;
        for (int round = 0; round < bestArrivalTimeInRound.length; round++) {
            if (bestArrivalTimeInRound[round][query.toStopId()] == bestArrivalTime[query.toStopId()]) {
                bestRound = round;
                break;
            }
        }
        if (bestRound <= 0) {
            return List.of();
        }

        List<PlanLeg> reversedLegs = new ArrayList<>();
        int stopId = query.toStopId();
        int round = bestRound;
        int guard = 0;
        while (stopId != query.fromStopId() && round > 0 && guard++ < stops.length + routes.size()) {
            RaptorParent parent = parents[round][stopId];
            if (parent == null) {
                return List.of();
            }
            if (parent.type() == RaptorParentType.TRANSFER) {
                reversedLegs.add(new PlanLeg(
                        LegType.PLATFORM_TRANSFER,
                        null,
                        parent.fromStopId(),
                        stopId,
                        parent.departureSeconds(),
                        parent.arrivalSeconds(),
                        List.of()
                ));
                stopId = parent.fromStopId();
            } else {
                RaptorRoute route = routes.get(parent.routeId());
                int tripId = tripId(parent.routeId(), parent.tripIndex());
                RaptorStopTime departure = stopTimes[route.stopTimesStart() + parent.tripIndex()*route.stops() + parent.fromRouteStopIndex()];
                RaptorStopTime arrival = stopTimes[route.stopTimesStart() + parent.tripIndex()*route.stops() + parent.toRouteStopIndex()];
                reversedLegs.add(new PlanLeg(
                        LegType.RIDE,
                        tripId,
                        parent.fromStopId(),
                        stopId,
                        departure.departureSeconds(),
                        arrival.arrivalSeconds(),
                        rideSegments(tripId, parent.fromStopId(), stopId)
                ));
                stopId = parent.fromStopId();
                round--;
            }
        }
        if (stopId != query.fromStopId()) {
            return List.of();
        }
        Collections.reverse(reversedLegs);
        return List.of(new RouteVariant("raptor-0", List.copyOf(reversedLegs)));
    }

    private int tripId(int routeId, int tripIndex) {
        int start = 0;
        for (int index = 0; index < routeId; index++) {
            start += routes.get(index).trips();
        }
        return routeTrips[start + tripIndex];
    }

    private List<Integer> rideSegments(int tripId, int fromStopId, int toStopId) {
        List<Integer> result = new ArrayList<>();
        boolean collecting = false;
        for (int segmentId : network.tripSegments(tripId)) {
            TripSegment segment = network.segment(segmentId);
            if (!collecting && segment.fromStopId() == fromStopId) {
                collecting = true;
            }
            if (collecting) {
                result.add(segmentId);
                if (segment.toStopId() == toStopId) {
                    return List.copyOf(result);
                }
            }
        }
        return List.of();
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

    private RaptorTables buildTables(TransitNetwork network) {
        Map<RoutePatternKey, RoutePatternBuilder> patterns = new LinkedHashMap<>();
        for (TransitTrip trip : network.trips()) {
            TripPattern tripPattern = tripPattern(network, trip.id());
            if (tripPattern.stopIds().size() < 2) {
                continue;
            }
            RoutePatternKey key = new RoutePatternKey(trip.routeShortName(), tripPattern.stopIds());
            patterns.computeIfAbsent(key, ignored -> new RoutePatternBuilder(tripPattern.stopIds()))
                    .trips()
                    .add(new PatternTrip(trip.id(), tripPattern.stopTimes()));
        }

        List<RaptorRoute> routes = new ArrayList<>();
        List<Integer> routeStops = new ArrayList<>();
        List<RaptorStopTime> stopTimes = new ArrayList<>();
        List<Integer> routeTrips = new ArrayList<>();
        List<LinkedHashSet<StopRouteEntry>> routeEntriesByStop = new ArrayList<>();
        for (int i = 0; i < network.stops().size(); i++) {
            routeEntriesByStop.add(new LinkedHashSet<>());
        }

        for (RoutePatternBuilder pattern : patterns.values()) {
            int routeId = routes.size();
            List<Integer> patternStops = pattern.stopIds();
            pattern.trips().sort(Comparator
                    .comparingInt((PatternTrip trip) -> trip.stopTimes().get(0).departureSeconds())
                    .thenComparingInt(PatternTrip::tripId));

            int routeStopsStart = routeStops.size();
            for (int routeStopIndex = 0; routeStopIndex < patternStops.size(); routeStopIndex++) {
                int stopId = patternStops.get(routeStopIndex);
                routeStops.add(stopId);
                routeEntriesByStop.get(stopId).add(new StopRouteEntry(routeId, routeStopIndex));
            }

            int stopTimesStart = stopTimes.size();
            for (PatternTrip trip : pattern.trips()) {
                routeTrips.add(trip.tripId());
                stopTimes.addAll(trip.stopTimes());
            }

            routes.add(new RaptorRoute(
                    pattern.trips().size(),
                    patternStops.size(),
                    stopTimesStart,
                    routeStopsStart
            ));
        }

        List<Integer> stopRoutes = new ArrayList<>();
        List<Integer> stopRouteIndexes = new ArrayList<>();
        List<Integer> transfers = new ArrayList<>();
        RaptorStop[] stops = new RaptorStop[network.stops().size()];
        for (int stopId = 0; stopId < network.stops().size(); stopId++) {
            int stopRoutesStart = stopRoutes.size();
            for (StopRouteEntry entry : routeEntriesByStop.get(stopId)) {
                stopRoutes.add(entry.routeId());
                stopRouteIndexes.add(entry.routeStopIndex());
            }

            int transfersStart = transfers.size();
            for (PlatformTransfer transfer : network.platformTransfersFrom(stopId)) {
                transfers.add(transfer.toStopId());
            }

            stops[stopId] = new RaptorStop(
                    stopRoutesStart,
                    stopRoutes.size(),
                    transfersStart,
                    transfers.size()
            );
        }

        return new RaptorTables(
                List.copyOf(routes),
                toIntArray(routeStops),
                stopTimes.toArray(RaptorStopTime[]::new),
                stops,
                toIntArray(stopRoutes),
                toIntArray(stopRouteIndexes),
                toIntArray(transfers),
                toIntArray(routeTrips)
        );
    }

    private TripPattern tripPattern(TransitNetwork network, int tripId) {
        List<Integer> segmentIds = network.tripSegments(tripId);
        List<Integer> stopIds = new ArrayList<>();
        List<RaptorStopTime> times = new ArrayList<>();
        for (int index = 0; index < segmentIds.size(); index++) {
            TripSegment segment = network.segment(segmentIds.get(index));
            if (index == 0) {
                stopIds.add(segment.fromStopId());
                times.add(new RaptorStopTime(segment.departureSeconds(), segment.departureSeconds()));
            }
            stopIds.add(segment.toStopId());
            int departure = index + 1 < segmentIds.size()
                    ? network.segment(segmentIds.get(index + 1)).departureSeconds()
                    : segment.arrivalSeconds();
            times.add(new RaptorStopTime(segment.arrivalSeconds(), departure));
        }
        return new TripPattern(List.copyOf(stopIds), List.copyOf(times));
    }

    private int[] toIntArray(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private record RaptorRoute(
            int trips,
            int stops,
            int stopTimesStart,
            int routeStopsStart
    ) {
    }

    private record RaptorStop(
            int stopRoutesStart,
            int stopRoutesEnd,
            int transfersStart,
            int transfersEnd
    ) {
    }

    private record RaptorStopTime(int arrivalSeconds, int departureSeconds) {
    }

    private enum RaptorParentType {
        RIDE,
        TRANSFER
    }

    private record RaptorParent(
            RaptorParentType type,
            int fromStopId,
            int routeId,
            int tripIndex,
            int fromRouteStopIndex,
            int toRouteStopIndex,
            int departureSeconds,
            int arrivalSeconds
    ) {
        private static RaptorParent ride(
                int fromStopId,
                int routeId,
                int tripIndex,
                int fromRouteStopIndex,
                int toRouteStopIndex
        ) {
            return new RaptorParent(RaptorParentType.RIDE, fromStopId, routeId, tripIndex, fromRouteStopIndex, toRouteStopIndex, 0, 0);
        }

        private static RaptorParent transfer(int fromStopId, int departureSeconds, int arrivalSeconds) {
            return new RaptorParent(RaptorParentType.TRANSFER, fromStopId, -1, -1, -1, -1, departureSeconds, arrivalSeconds);
        }
    }

    private record RaptorTables(
            List<RaptorRoute> routes,
            int[] routeStops,
            RaptorStopTime[] stopTimes,
            RaptorStop[] stops,
            int[] stopRoutes,
            int[] stopRouteIndexes,
            int[] transfers,
            int[] routeTrips
    ) {
    }

    private record TripPattern(List<Integer> stopIds, List<RaptorStopTime> stopTimes) {
    }

    private record RoutePatternKey(String routeShortName, List<Integer> stopIds) {
    }

    private record PatternTrip(int tripId, List<RaptorStopTime> stopTimes) {
    }

    private record RoutePatternBuilder(List<Integer> stopIds, List<PatternTrip> trips) {
        private RoutePatternBuilder(List<Integer> stopIds) {
            this(List.copyOf(stopIds), new ArrayList<>());
        }
    }

    private record StopRouteEntry(int routeId, int routeStopIndex) {
    }
}
