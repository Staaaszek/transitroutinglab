package pl.edu.pitp.transit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.gtfs")
public class GtfsProperties {
    private String cacheDir = "../data/gtfs";
    private boolean refreshOnStart;
    private List<FeedConfig> feeds = new ArrayList<>();

    public String getCacheDir() {
        return cacheDir;
    }

    public void setCacheDir(String cacheDir) {
        this.cacheDir = cacheDir;
    }

    public boolean isRefreshOnStart() {
        return refreshOnStart;
    }

    public void setRefreshOnStart(boolean refreshOnStart) {
        this.refreshOnStart = refreshOnStart;
    }

    public List<FeedConfig> getFeeds() {
        return feeds;
    }

    public void setFeeds(List<FeedConfig> feeds) {
        this.feeds = feeds;
    }

    public static class FeedConfig {
        private String id;
        private String name;
        private List<String> urls = new ArrayList<>();
        private boolean enabled = true;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getUrls() {
            return urls;
        }

        public void setUrls(List<String> urls) {
            this.urls = urls;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

}
