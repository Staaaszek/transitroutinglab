package pl.edu.pitp.transit.engine;

import java.util.List;

public record RouteVariant(String id, List<PlanLeg> legs) {
}
