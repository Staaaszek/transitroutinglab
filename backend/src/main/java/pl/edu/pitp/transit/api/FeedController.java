package pl.edu.pitp.transit.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.edu.pitp.transit.feed.FeedCatalog;

import java.util.List;

@RestController
@RequestMapping("/api/feeds")
public class FeedController {
    private final FeedCatalog feedCatalog;

    public FeedController(FeedCatalog feedCatalog) {
        this.feedCatalog = feedCatalog;
    }

    @GetMapping
    public List<ApiDtos.FeedDto> feeds() {
        return feedCatalog.feeds().stream()
                .map(feed -> new ApiDtos.FeedDto(
                        feed.id(),
                        feed.name(),
                        feed.network().stops().size(),
                        feed.network().segments().size(),
                        feed.network().trips().size()
                ))
                .toList();
    }
}
