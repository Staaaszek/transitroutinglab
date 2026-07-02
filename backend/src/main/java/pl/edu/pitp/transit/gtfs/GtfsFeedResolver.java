package pl.edu.pitp.transit.gtfs;

import org.springframework.stereotype.Component;
import pl.edu.pitp.transit.config.GtfsProperties;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Component
public class GtfsFeedResolver {
    private final GtfsProperties properties;

    public GtfsFeedResolver(GtfsProperties properties) {
        this.properties = properties;
    }

    public List<ResolvedFeed> resolveEnabledFeeds() throws IOException {
        List<ResolvedFeed> result = new ArrayList<>();
        for (GtfsProperties.FeedConfig feed : properties.getFeeds()) {
            if (!feed.isEnabled()) {
                continue;
            }
            result.add(resolve(feed));
        }
        return result;
    }

    private ResolvedFeed resolve(GtfsProperties.FeedConfig feed) throws IOException {
        Path feedCacheDir = Path.of(properties.getCacheDir()).resolve(feed.getId());
        Files.createDirectories(feedCacheDir);
        List<Path> archives = new ArrayList<>();
        for (String url : feed.getUrls()) {
            URI uri = URI.create(url);
            String fileName = Path.of(uri.getPath()).getFileName().toString();
            Path archive = feedCacheDir.resolve(fileName);
            if (properties.isRefreshOnStart() || Files.notExists(archive)) {
                try (var in = uri.toURL().openStream()) {
                    Files.copy(in, archive, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            archives.add(archive);
        }
        String name = feed.getName() == null || feed.getName().isBlank() ? feed.getId() : feed.getName();
        return new ResolvedFeed(feed.getId(), name, List.copyOf(archives));
    }

    public record ResolvedFeed(String id, String name, List<Path> archives) {
    }
}
