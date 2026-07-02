package pl.edu.pitp.transit.feed;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import pl.edu.pitp.transit.gtfs.GtfsFeedResolver;
import pl.edu.pitp.transit.gtfs.GtfsImporter;
import pl.edu.pitp.transit.model.TransitNetwork;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FeedCatalog {
    private final GtfsFeedResolver resolver;
    private final GtfsImporter importer;
    private final Map<String, LoadedFeed> feeds = new LinkedHashMap<>();

    public FeedCatalog(GtfsFeedResolver resolver, GtfsImporter importer) {
        this.resolver = resolver;
        this.importer = importer;
    }

    @PostConstruct
    void load() throws IOException {
        for (GtfsFeedResolver.ResolvedFeed resolvedFeed : resolver.resolveEnabledFeeds()) {
            TransitNetwork network = importer.importFeed(resolvedFeed);
            feeds.put(resolvedFeed.id(), new LoadedFeed(resolvedFeed.id(), resolvedFeed.name(), network));
        }
    }

    public List<LoadedFeed> feeds() {
        return List.copyOf(feeds.values());
    }

    public LoadedFeed get(String feedId) {
        if (feedId == null || feedId.isBlank()) {
            return feeds.values().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("No GTFS feeds are configured."));
        }
        LoadedFeed feed = feeds.get(feedId);
        if (feed == null) {
            throw new IllegalArgumentException("Unknown feed: " + feedId);
        }
        return feed;
    }

    public record LoadedFeed(String id, String name, TransitNetwork network) {
    }
}
