package manjosh.labs.consistenthashing.config;

import jakarta.annotation.PostConstruct;
import manjosh.labs.consistenthashing.core.ConsistentHashRing;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;

/**
 * RingInitializer — Single Responsibility: seed the ConsistentHashRing at startup.
 *
 * Runs once after all Spring beans are created (@PostConstruct).
 * Reads node names from dataSourceMap keys ("ds0", "ds1")
 * and registers each into the ring.
 *
 * Why @PostConstruct and not @Bean?
 *   The ring is already a Spring bean (created by Spring).
 *   We just need to call ring.addNode() on it after the dataSourceMap is ready.
 *   @PostConstruct is the standard hook for "run this after dependency injection is done".
 *
 * Adding a new shard (ds2):
 *   → add it to application.properties ONLY
 *   → this class automatically picks it up — no code change needed (Open/Closed Principle ✅)
 */
@Component
public class RingInitializer {

    private final ConsistentHashRing ring;
    private final Map<String, DataSource> dataSourceMap;

    public RingInitializer(ConsistentHashRing ring, Map<String, DataSource> dataSourceMap) {
        this.ring = ring;
        this.dataSourceMap = dataSourceMap;
    }

    @PostConstruct
    public void initRing() {
        // dataSourceMap keys are shard names: "ds0", "ds1", ...
        // Same names are used in ring.addNode() → the ring and the map share identical keys
        for (String shardName : dataSourceMap.keySet()) {
            ring.addNode(shardName);
        }
    }
}
