package pl.edu.pitp.transit.engine;

import pl.edu.pitp.transit.model.TransitNetwork;

import java.util.List;

public interface RoutingEngineFactory {
    String id();

    String displayName();

    List<EngineParameter> parameters();

    RoutingEngine create(TransitNetwork network);
}
