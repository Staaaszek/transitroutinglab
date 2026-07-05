package pl.edu.pitp.transit.engine;

import org.springframework.stereotype.Component;
import pl.edu.pitp.transit.model.TransitNetwork;

import java.util.List;

@Component
public class RaptorEngineFactory implements RoutingEngineFactory {
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
    public RoutingEngine create(TransitNetwork network) {
        return new RaptorEngine(network);
    }
}
