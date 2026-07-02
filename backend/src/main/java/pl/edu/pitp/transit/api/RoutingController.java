package pl.edu.pitp.transit.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.edu.pitp.transit.service.RoutingService;

@RestController
@RequestMapping("/api/routing")
public class RoutingController {
    private final RoutingService routingService;

    public RoutingController(RoutingService routingService) {
        this.routingService = routingService;
    }

    @PostMapping("/search")
    public ApiDtos.RoutingResponse search(@Valid @RequestBody ApiDtos.RoutingRequest request) {
        return routingService.search(request);
    }
}
