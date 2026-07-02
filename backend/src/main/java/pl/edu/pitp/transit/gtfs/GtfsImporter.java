package pl.edu.pitp.transit.gtfs;

import org.springframework.stereotype.Component;
import pl.edu.pitp.transit.model.PlatformTransfer;
import pl.edu.pitp.transit.model.ServiceCalendar;
import pl.edu.pitp.transit.model.TransitNetwork;
import pl.edu.pitp.transit.model.TransitStop;
import pl.edu.pitp.transit.model.TransitTrip;
import pl.edu.pitp.transit.model.TripSegment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

@Component
public class GtfsImporter {
    public TransitNetwork importFeed(GtfsFeedResolver.ResolvedFeed feed) throws IOException {
        ImportState state = new ImportState(feed.id());
        int archiveIndex = 0;
        for (Path archive : feed.archives()) {
            importArchive(feed.id(), archiveIndex++, archive, state);
        }
        List<List<Integer>> outgoing = groupSegmentsByFromStop(state.stops.size(), state.segments);
        List<List<Integer>> byTrip = groupSegmentsByTrip(state.trips.size(), state.segments);
        List<List<PlatformTransfer>> platformTransfers = buildPlatformCliques(state.stops, state.stopsByName);
        validatePlatformCliques(state.stopsByName, platformTransfers);
        return new TransitNetwork(
                feed.id(),
                List.copyOf(state.stops),
                List.copyOf(state.segments),
                List.copyOf(state.trips),
                outgoing,
                byTrip,
                platformTransfers,
                Map.copyOf(state.stopExternalIds),
                Map.copyOf(state.calendars)
        );
    }

    private void importArchive(String feedId, int archiveIndex, Path archive, ImportState state) throws IOException {
        Map<String, CsvTable> tables = readTables(archive);
        String source = feedId + "#" + archiveIndex;
        Map<String, Integer> routes = new HashMap<>();
        Map<String, String> routeShortNames = new HashMap<>();

        for (Map<String, String> row : rows(tables, "routes.txt")) {
            String externalRouteId = sourceKey(source, row.get("route_id"));
            int routeId = routes.size();
            routes.put(externalRouteId, routeId);
            routeShortNames.put(externalRouteId, valueOr(row.get("route_short_name"), row.get("route_long_name")));
        }

        importCalendars(source, tables, state);
        importStops(source, tables, state);

        Map<String, RawTrip> rawTrips = new HashMap<>();
        for (Map<String, String> row : rows(tables, "trips.txt")) {
            String externalTripId = sourceKey(source, row.get("trip_id"));
            String externalRouteId = sourceKey(source, row.get("route_id"));
            rawTrips.put(externalTripId, new RawTrip(
                    externalTripId,
                    routes.getOrDefault(externalRouteId, -1),
                    routeShortNames.getOrDefault(externalRouteId, row.get("route_id")),
                    row.getOrDefault("trip_headsign", ""),
                    sourceKey(source, row.get("service_id"))
            ));
        }

        Map<String, List<RawStopTime>> stopTimesByTrip = new HashMap<>();
        for (Map<String, String> row : rows(tables, "stop_times.txt")) {
            String tripId = sourceKey(source, row.get("trip_id"));
            String stopKey = sourceKey(source, row.get("stop_id"));
            Integer stopId = state.stopExternalIds.get(stopKey);
            if (stopId == null) {
                continue;
            }
            stopTimesByTrip.computeIfAbsent(tripId, ignored -> new ArrayList<>()).add(new RawStopTime(
                    stopId,
                    parseGtfsSeconds(row.get("arrival_time")),
                    parseGtfsSeconds(row.get("departure_time")),
                    parseInt(row.get("stop_sequence"), 0)
            ));
        }

        for (Map.Entry<String, List<RawStopTime>> entry : stopTimesByTrip.entrySet()) {
            RawTrip rawTrip = rawTrips.get(entry.getKey());
            if (rawTrip == null) {
                continue;
            }
            List<RawStopTime> stopTimes = entry.getValue().stream()
                    .sorted(Comparator.comparingInt(RawStopTime::sequence))
                    .toList();
            int tripId = state.trips.size();
            List<Integer> segmentIds = new ArrayList<>();
            for (int i = 0; i < stopTimes.size() - 1; i++) {
                RawStopTime from = stopTimes.get(i);
                RawStopTime to = stopTimes.get(i + 1);
                int segmentId = state.segments.size();
                state.segments.add(new TripSegment(
                        segmentId,
                        tripId,
                        rawTrip.routeId(),
                        rawTrip.routeShortName(),
                        from.stopId(),
                        to.stopId(),
                        from.departureSeconds(),
                        to.arrivalSeconds(),
                        from.sequence()
                ));
                segmentIds.add(segmentId);
            }
            state.trips.add(new TransitTrip(
                    tripId,
                    feedId,
                    rawTrip.externalId(),
                    rawTrip.routeId(),
                    rawTrip.routeShortName(),
                    rawTrip.headsign(),
                    rawTrip.serviceId(),
                    List.copyOf(segmentIds)
            ));
        }
    }

    private void importCalendars(String source, Map<String, CsvTable> tables, ImportState state) {
        for (Map<String, String> row : rows(tables, "calendar.txt")) {
            state.calendars.put(sourceKey(source, row.get("service_id")), parseCalendar(row));
        }
        for (Map<String, String> row : rows(tables, "calendar_dates.txt")) {
            String serviceId = sourceKey(source, row.get("service_id"));
            ServiceCalendar previous = state.calendars.get(serviceId);
            Map<LocalDate, Integer> exceptions = new HashMap<>();
            if (previous != null) {
                exceptions.putAll(previous.exceptions());
            }
            exceptions.put(parseDate(row.get("date")), parseInt(row.get("exception_type"), 1));
            ServiceCalendar next = previous == null
                    ? new ServiceCalendar(null, null, Set.of(), exceptions)
                    : new ServiceCalendar(previous.startDate(), previous.endDate(), previous.activeDays(), exceptions);
            state.calendars.put(serviceId, next);
        }
    }

    private void importStops(String source, Map<String, CsvTable> tables, ImportState state) {
        for (Map<String, String> row : rows(tables, "stops.txt")) {
            String externalId = sourceKey(source, row.get("stop_id"));
            int id = state.stops.size();
            TransitStop stop = new TransitStop(
                    id,
                    state.feedId,
                    externalId,
                    row.getOrDefault("stop_name", ""),
                    row.getOrDefault("stop_code", ""),
                    row.getOrDefault("platform_code", ""),
                    parseDouble(row.get("stop_lat")),
                    parseDouble(row.get("stop_lon"))
            );
            state.stops.add(stop);
            state.stopExternalIds.put(externalId, id);
            state.stopsByName.computeIfAbsent(normalizeName(stop.name()), ignored -> new ArrayList<>()).add(id);
        }
    }

    private Map<String, CsvTable> readTables(Path archive) throws IOException {
        Map<String, CsvTable> result = new HashMap<>();
        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            for (String file : List.of("stops.txt", "routes.txt", "trips.txt", "stop_times.txt", "calendar.txt", "calendar_dates.txt")) {
                var entry = zip.getEntry(file);
                if (entry == null) {
                    continue;
                }
                try (var in = zip.getInputStream(entry)) {
                    result.put(file, CsvTable.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
                }
            }
        }
        return result;
    }

    private List<Map<String, String>> rows(Map<String, CsvTable> tables, String name) {
        CsvTable table = tables.get(name);
        return table == null ? List.of() : table.rows();
    }

    private List<List<Integer>> groupSegmentsByFromStop(int stopCount, List<TripSegment> segments) {
        List<List<Integer>> result = mutableSlots(stopCount);
        for (TripSegment segment : segments) {
            result.get(segment.fromStopId()).add(segment.id());
        }
        result.forEach(list -> list.sort(Comparator.comparingInt(id -> segments.get(id).departureSeconds())));
        return freezeSlots(result);
    }

    private List<List<Integer>> groupSegmentsByTrip(int tripCount, List<TripSegment> segments) {
        List<List<Integer>> result = mutableSlots(tripCount);
        for (TripSegment segment : segments) {
            result.get(segment.tripId()).add(segment.id());
        }
        result.forEach(list -> list.sort(Comparator.comparingInt(id -> segments.get(id).sequence())));
        return freezeSlots(result);
    }

    private List<List<PlatformTransfer>> buildPlatformCliques(List<TransitStop> stops, Map<String, List<Integer>> stopsByName) {
        List<List<PlatformTransfer>> graph = new ArrayList<>();
        for (int i = 0; i < stops.size(); i++) {
            graph.add(new ArrayList<>());
        }
        for (List<Integer> group : stopsByName.values()) {
            if (group.size() < 2) {
                continue;
            }
            for (int from : group) {
                for (int to : group) {
                    if (from != to) {
                        graph.get(from).add(new PlatformTransfer(from, to));
                    }
                }
            }
        }
        return graph.stream().map(List::copyOf).toList();
    }

    private void validatePlatformCliques(Map<String, List<Integer>> stopsByName, List<List<PlatformTransfer>> graph) {
        for (Map.Entry<String, List<Integer>> entry : stopsByName.entrySet()) {
            List<Integer> group = entry.getValue();
            if (group.size() < 2) {
                continue;
            }
            Set<String> edges = new HashSet<>();
            for (int from : group) {
                for (PlatformTransfer transfer : graph.get(from)) {
                    edges.add(transfer.fromStopId() + ">" + transfer.toStopId());
                }
            }
            for (int from : group) {
                for (int to : group) {
                    if (from != to && !edges.contains(from + ">" + to)) {
                        throw new IllegalStateException("Platform clique is not closed for stop_name=" + entry.getKey());
                    }
                }
            }
        }
    }

    private <T> List<List<T>> mutableSlots(int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            result.add(new ArrayList<>());
        }
        return result;
    }

    private <T> List<List<T>> freezeSlots(List<List<T>> slots) {
        return slots.stream().map(List::copyOf).toList();
    }

    private ServiceCalendar parseCalendar(Map<String, String> row) {
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        addDay(days, row, "monday", DayOfWeek.MONDAY);
        addDay(days, row, "tuesday", DayOfWeek.TUESDAY);
        addDay(days, row, "wednesday", DayOfWeek.WEDNESDAY);
        addDay(days, row, "thursday", DayOfWeek.THURSDAY);
        addDay(days, row, "friday", DayOfWeek.FRIDAY);
        addDay(days, row, "saturday", DayOfWeek.SATURDAY);
        addDay(days, row, "sunday", DayOfWeek.SUNDAY);
        return new ServiceCalendar(parseDate(row.get("start_date")), parseDate(row.get("end_date")), days, Map.of());
    }

    private void addDay(Set<DayOfWeek> days, Map<String, String> row, String key, DayOfWeek day) {
        if ("1".equals(row.get(key))) {
            days.add(day);
        }
    }

    private String sourceKey(String source, String id) {
        return source + ":" + id;
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private String valueOr(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.of(Integer.parseInt(value.substring(0, 4)), Integer.parseInt(value.substring(4, 6)), Integer.parseInt(value.substring(6, 8)));
    }

    private int parseGtfsSeconds(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        String[] parts = value.split(":");
        return Integer.parseInt(parts[0]) * 3600 + Integer.parseInt(parts[1]) * 60 + Integer.parseInt(parts[2]);
    }

    private int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Integer.parseInt(value);
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Double.parseDouble(value);
    }

    private record RawTrip(String externalId, int routeId, String routeShortName, String headsign, String serviceId) {
    }

    private record RawStopTime(int stopId, int arrivalSeconds, int departureSeconds, int sequence) {
    }

    private static class ImportState {
        private final String feedId;
        private final List<TransitStop> stops = new ArrayList<>();
        private final List<TripSegment> segments = new ArrayList<>();
        private final List<TransitTrip> trips = new ArrayList<>();
        private final Map<String, Integer> stopExternalIds = new LinkedHashMap<>();
        private final Map<String, List<Integer>> stopsByName = new HashMap<>();
        private final Map<String, ServiceCalendar> calendars = new HashMap<>();

        private ImportState(String feedId) {
            this.feedId = feedId;
        }
    }
}
