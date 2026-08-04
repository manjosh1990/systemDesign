package manjosh.labs.consistenthashing.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import manjosh.labs.consistenthashing.service.DataRebalancerService;
import manjosh.labs.consistenthashing.service.ShardManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin")
@Tag(name = "Control Plane", description = "Admin APIs for dynamically managing the database cluster topology without downtime")
public class AdminController {

    private final ShardManagementService shardManagementService;
    private final DataRebalancerService dataRebalancerService;

    public AdminController(ShardManagementService shardManagementService, DataRebalancerService dataRebalancerService) {
        this.shardManagementService = shardManagementService;
        this.dataRebalancerService = dataRebalancerService;
    }

    @PostMapping("/shards")
    @Operation(
            summary = "Dynamically add a new Database Shard",
            description = "Provisions a new PostgreSQL shard by running Flyway migrations, registers it in the master metadata table, and inserts it into the live Consistent Hash Ring.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\n  \"name\": \"ds1\",\n  \"url\": \"jdbc:postgresql://postgres-ds1:5433/ds_1\",\n  \"username\": \"postgres\",\n  \"password\": \"password\"\n}"
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Shard successfully registered and activated"),
                    @ApiResponse(responseCode = "400", description = "Missing required connection credentials")
            }
    )
    public ResponseEntity<String> addShard(@RequestBody Map<String, String> payload) {
        String name = payload.get("name");
        String url = payload.get("url");
        String username = payload.get("username");
        String password = payload.get("password");

        if (name == null || url == null || username == null || password == null) {
            return ResponseEntity.badRequest().body("Missing required fields (name, url, username, password)");
        }

        shardManagementService.registerNewShard(name, url, username, password);
        return ResponseEntity.ok("Shard " + name + " successfully added and activated in the Consistent Hash Ring!");
    }

    @PostMapping("/rebalance")
    @Operation(
            summary = "Trigger a Data Rebalance (Migration)",
            description = "Scans all active databases to find 'stranded' records that belong to a new node, and physically migrates them to their correct shard.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Rebalance completed successfully, returning the number of records moved.")
            }
    )
    public ResponseEntity<String> rebalance() {
        int movedCount = dataRebalancerService.rebalanceData();
        return ResponseEntity.ok("Rebalance complete! Successfully moved " + movedCount + " stranded records to their new correct shards.");
    }
}
