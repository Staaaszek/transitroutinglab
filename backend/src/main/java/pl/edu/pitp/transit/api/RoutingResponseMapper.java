package pl.edu.pitp.transit.api;

import org.springframework.stereotype.Component;
import pl.edu.pitp.transit.engine.LegType;
import pl.edu.pitp.transit.engine.PlanLeg;
import pl.edu.pitp.transit.engine.RoutePlan;
import pl.edu.pitp.transit.engine.RouteVariant;
import pl.edu.pitp.transit.model.GeoPoint;
import pl.edu.pitp.transit.model.TransitNetwork;
import pl.edu.pitp.transit.model.TransitStop;
import pl.edu.pitp.transit.model.TransitTrip;
import pl.edu.pitp.transit.model.TripSegment;

import java.util.ArrayList;
import java.util.List;

@Component
public class RoutingResponseMapper {
    public ApiDtos.RoutingResponse toResponse(String feedId, String engineId, RoutePlan plan, TransitNetwork network) {
        return new ApiDtos.RoutingResponse(
                feedId,
                engineId,
                plan.routes().stream().map(route -> route(route, network)).toList(),
                plan.diagnostics()
        );
    }

    private ApiDtos.RouteDto route(RouteVariant variant, TransitNetwork network) {
        List<ApiDtos.RouteLegDto> legs = variant.legs().stream().map(leg -> leg(leg, network)).toList();
        List<GeoPoint> geometry = legs.stream().flatMap(leg -> leg.geometry().stream()).toList();
        int departure = legs.isEmpty() ? 0 : legs.get(0).departureSeconds();
        int arrival = legs.isEmpty() ? 0 : legs.get(legs.size() - 1).arrivalSeconds();
        int transfers = (int) legs.stream().filter(leg -> "platform-transfer".equals(leg.type())).count();
        String title = legs.stream()
                .filter(leg -> !"platform-transfer".equals(leg.type()))
                .map(ApiDtos.RouteLegDto::routeShortName)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("Transfer");
        return new ApiDtos.RouteDto(
                variant.id(),
                new ApiDtos.RouteSummaryDto(title, departure, arrival, Math.max(0, arrival - departure), transfers),
                legs,
                geometry
        );
    }

    private ApiDtos.RouteLegDto leg(PlanLeg leg, TransitNetwork network) {
        TransitStop from = network.stop(leg.fromStopId());
        TransitStop to = network.stop(leg.toStopId());
        TransitTrip trip = leg.tripId() == null ? null : network.trip(leg.tripId());
        return new ApiDtos.RouteLegDto(
                leg.type() == LegType.PLATFORM_TRANSFER ? "platform-transfer" : "ride",
                leg.tripId(),
                trip == null ? "" : trip.routeShortName(),
                trip == null ? "" : trip.headsign(),
                from.id(),
                from.name(),
                to.id(),
                to.name(),
                leg.departureSeconds(),
                leg.arrivalSeconds(),
                geometry(leg, network)
        );
    }

    private List<GeoPoint> geometry(PlanLeg leg, TransitNetwork network) {
        if (leg.segmentIds().isEmpty()) {
            TransitStop from = network.stop(leg.fromStopId());
            TransitStop to = network.stop(leg.toStopId());
            return List.of(new GeoPoint(from.lat(), from.lon()), new GeoPoint(to.lat(), to.lon()));
        }
        List<GeoPoint> points = new ArrayList<>();
        for (int segmentId : leg.segmentIds()) {
            TripSegment segment = network.segment(segmentId);
            TransitStop from = network.stop(segment.fromStopId());
            TransitStop to = network.stop(segment.toStopId());
            if (points.isEmpty()) {
                points.add(new GeoPoint(from.lat(), from.lon()));
            }
            points.add(new GeoPoint(to.lat(), to.lon()));
        }
        return points;
    }
}
