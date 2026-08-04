package manjosh.labs.consistenthashing.controller;

import manjosh.labs.consistenthashing.core.ConsistentHashRing;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;

/**
 * RingDebugController — exposes ring internals for observability.
 *
 * Use this to verify:
 *   - How many vnodes each physical node has
 *   - Which shard a given key would route to
 *   - Simulated distribution across N keys (detect hotspots)
 *
 * In production, you'd gate this behind an admin role or actuator endpoint.
 * For a learning POC, it's invaluable for understanding the ring behavior.
 */
@RestController
@RequestMapping("/debug/ring")
public class RingDebugController {

    private final ConsistentHashRing ring;

    public RingDebugController(ConsistentHashRing ring) {
        this.ring = ring;
    }

    /**
     * GET /debug/ring/stats
     * Returns: how many virtual nodes each physical node owns.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        SortedMap<Long, String> snapshot = ring.getRingSnapshot();

        // Count vnodes per physical node
        Map<String, Integer> vnodeCounts = new HashMap<>();
        for (String node : snapshot.values()) {
            vnodeCounts.merge(node, 1, Integer::sum);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalVnodes", snapshot.size());
        response.put("physicalNodes", ring.getNodeCount());
        response.put("vnodesPerNode", vnodeCounts);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /debug/ring/lookup?key=user:42
     * Returns: which shard owns this key.
     */
    @GetMapping("/lookup")
    public ResponseEntity<Map<String, String>> lookup(@RequestParam String key) {
        String node = ring.getNode(key);
        return ResponseEntity.ok(Map.of("key", key, "shard", node));
    }

    /**
     * GET /debug/ring/distribution?keys=1000
     * Simulates N user IDs and shows how they distribute across shards.
     * Use this to verify uniform distribution.
     *
     * Example output: {"ds0": 512, "ds1": 488} — nearly 50/50 for 2 shards.
     */
    @GetMapping("/distribution")
    public ResponseEntity<Map<String, Object>> distribution(
            @RequestParam(defaultValue = "1000") int keys) {

        Map<String, Integer> distribution = new HashMap<>();

        for (int i = 1; i <= keys; i++) {
            String node = ring.getNode(String.valueOf(i));
            distribution.merge(node, 1, Integer::sum);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalKeys", keys);
        response.put("distribution", distribution);

        // Calculate skew percentage
        if (!distribution.isEmpty()) {
            int max = distribution.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            int min = distribution.values().stream().mapToInt(Integer::intValue).min().orElse(0);
            int ideal = keys / distribution.size();
            double skewPercent = ideal > 0 ? ((max - min) / (double) ideal) * 100 : 0;
            response.put("idealPerNode", ideal);
            response.put("skewPercent", String.format("%.1f%%", skewPercent));
        }

        return ResponseEntity.ok(response);
    }
}
