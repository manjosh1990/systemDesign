package manjosh.labs.consistenthashing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ShardProperties — reads shard connection details from application.properties.
 *
 * Mirrors FlywayMigrationProperties in the sharding project.
 *
 * Example config it reads:
 *   app.shards[0].name=ds0
 *   app.shards[0].url=jdbc:postgresql://localhost:5432/ds_0
 *   app.shards[0].username=postgres
 *   app.shards[0].password=password
 *
 *   app.shards[1].name=ds1
 *   app.shards[1].url=jdbc:postgresql://localhost:5433/ds_1
 *   ...
 *
 * The "name" field (ds0, ds1) is also the key used in the ConsistentHashRing.
 * So when the ring returns "ds0", we look up shards by that name to get the connection.
 */
@Component
@ConfigurationProperties(prefix = "app")
public class ShardProperties {

    // Bound from app.shards[0], app.shards[1], etc.
    private List<Shard> shards;

    public List<Shard> getShards() {
        return shards;
    }

    public void setShards(List<Shard> shards) {
        this.shards = shards;
    }

    public static class Shard {

        // This name MUST match what we pass to ring.addNode(name)
        // e.g. "ds0", "ds1" — the ring uses this as the node identifier
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
