package pl.edu.pitp.transit.engine;

import pl.edu.pitp.transit.model.TransitNetwork;

import java.util.List;

public interface RoutingEngine {
    String id();

    String displayName();

    List<EngineParameter> parameters();

    RoutePlan findRoutes(RoutingQuery query, TransitNetwork network);
}
