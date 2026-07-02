package pl.edu.pitp.transit.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.edu.pitp.transit.engine.RoutingEngineRegistry;

import java.util.List;

@RestController
@RequestMapping("/api/routing/engines")
public class EngineController {
    private final RoutingEngineRegistry registry;

    public EngineController(RoutingEngineRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<ApiDtos.EngineDto> engines() {
        return registry.all().stream()
                .map(engine -> new ApiDtos.EngineDto(engine.id(), engine.displayName(), engine.parameters()))
                .toList();
    }
}
