package com.manjoshlabs.com.sharding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.migration")
public class FlywayMigrationProperties {

    private List<ShardDataSource> shards;

    public List<ShardDataSource> getShards() {
        return shards;
    }

    public void setShards(List<ShardDataSource> shards) {
        this.shards = shards;
    }

    public static class ShardDataSource {
        private String name;
        private String url;
        private String username;
        private String password;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
