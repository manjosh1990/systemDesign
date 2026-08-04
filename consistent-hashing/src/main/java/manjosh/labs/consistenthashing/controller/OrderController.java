package manjosh.labs.consistenthashing.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import manjosh.labs.consistenthashing.entity.Order;
import manjosh.labs.consistenthashing.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@Tag(name = "Order API", description = "Business API for Orders, seamlessly routed across shards using Consistent Hashing.")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(
            summary = "Create a new Order",
            description = "Creates a new order. The database shard is chosen automatically based on the hash of the userId.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\n  \"orderId\": 100,\n  \"userId\": 42,\n  \"status\": \"PENDING\"\n}"
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Order created and persisted to the correct shard")
            }
    )
    public ResponseEntity<Order> createOrder(@RequestBody Order order) {
        Order savedOrder = orderService.createOrder(order);
        return ResponseEntity.ok(savedOrder);
    }

    @GetMapping("/{userId}/{orderId}")
    @Operation(
            summary = "Get an Order by User ID and Order ID",
            description = "Fetches an order. Note: userId is required in the path because it is the Shard Key used for routing the query to the correct database.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Order found"),
                    @ApiResponse(responseCode = "404", description = "Order not found (might be stranded on an old node if a rebalance is needed!)")
            }
    )
    public ResponseEntity<Order> getOrder(
            @Parameter(description = "The Shard Key (used to find the database)") @PathVariable Long userId,
            @Parameter(description = "The actual Order ID") @PathVariable Long orderId) {
        Order order = orderService.getOrder(userId, orderId);
        return ResponseEntity.ok(order);
    }

    @GetMapping("/user/{userId}")
    @Operation(
            summary = "Get all Orders for a User",
            description = "Fetches all orders belonging to a specific user. It hashes the userId to instantly find the correct database shard, then runs the query.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of orders returned")
            }
    )
    public ResponseEntity<List<Order>> getOrdersByUserId(
            @Parameter(description = "The Shard Key (used to route the query)") @PathVariable Long userId) {
        List<Order> orders = orderService.getOrdersByUserId(userId);
        return ResponseEntity.ok(orders);
    }

    @PutMapping("/{userId}/{orderId}/status")
    @Operation(
            summary = "Update Order Status",
            description = "Updates the status of an existing order. Routes to the correct shard using the userId."
    )
    public ResponseEntity<Void> updateOrderStatus(
            @Parameter(description = "The Shard Key") @PathVariable Long userId, 
            @Parameter(description = "The Order ID") @PathVariable Long orderId, 
            @Parameter(description = "The new status (e.g. COMPLETED)") @RequestParam String status) {
        orderService.updateOrderStatus(userId, orderId, status);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{userId}/{orderId}")
    @Operation(
            summary = "Delete an Order",
            description = "Deletes an order from the correct shard based on the userId."
    )
    public ResponseEntity<Void> deleteOrder(
            @Parameter(description = "The Shard Key") @PathVariable Long userId,
            @Parameter(description = "The Order ID") @PathVariable Long orderId) {
        orderService.deleteOrder(userId, orderId);
        return ResponseEntity.noContent().build();
    }
}
