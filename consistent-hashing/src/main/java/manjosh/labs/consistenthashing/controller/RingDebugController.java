package manjosh.labs.consistenthashing.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@RestController
@RequestMapping("/debug/ring")
@Tag(name = "Ring Debugger", description = "Observability APIs for visualizing the internals of the Consistent Hash Ring.")
public class RingDebugController {

    private final ConsistentHashRing ring;

    public RingDebugController(ConsistentHashRing ring) {
        this.ring = ring;
    }

    @GetMapping("/stats")
    @Operation(
            summary = "Get Ring Statistics",
            description = "Returns the total number of Virtual Nodes and how they are distributed across the physical active Database Shards."
    )
    public ResponseEntity<Map<String, Object>> stats() {
        SortedMap<Long, String> snapshot = ring.getRingSnapshot();

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

    @GetMapping("/lookup")
    @Operation(
            summary = "Lookup Shard by Key",
            description = "Performs a mathematical lookup to determine exactly which Database Shard currently owns a specific key. This does NOT hit the database, it purely queries the Hash Ring."
    )
    public ResponseEntity<Map<String, String>> lookup(
            @Parameter(description = "The Shard Key (e.g. userId)") @RequestParam String key) {
        String node = ring.getNode(key);
        return ResponseEntity.ok(Map.of("key", key, "shard", node));
    }

    @GetMapping("/distribution")
    @Operation(
            summary = "Simulate Key Distribution",
            description = "Simulates routing N keys through the Hash Ring to verify that the load is distributed evenly across all shards, proving the effectiveness of our MurmurHash3 algorithm."
    )
    public ResponseEntity<Map<String, Object>> distribution(
            @Parameter(description = "Number of keys to simulate") @RequestParam(defaultValue = "1000") int keys) {

        Map<String, Integer> distribution = new HashMap<>();

        for (int i = 1; i <= keys; i++) {
            String node = ring.getNode(String.valueOf(i));
            distribution.merge(node, 1, Integer::sum);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalKeys", keys);
        response.put("distribution", distribution);

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
