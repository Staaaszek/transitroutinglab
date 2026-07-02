package pl.edu.pitp.transit.engine;

import java.util.List;

public record RoutePlan(List<RouteVariant> routes, SearchDiagnostics diagnostics) {
}
