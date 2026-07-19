package com.manjoshlabs.com.sharding.controller;

import com.manjoshlabs.com.sharding.entity.Order;
import com.manjoshlabs.com.sharding.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@RestController
@RequestMapping("/orders")
@Tag(name = "Order Management", description = "Endpoints for creating and managing sharded orders. Pay attention to how the userId is used as a shard key!")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "Create a new order", description = "Creates a new order. ShardingSphere will automatically route this to ds_0 or ds_1 based on the userId (user_id % 2).")
    @ApiResponse(responseCode = "201", description = "Order created successfully")
    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody Order order) {
        Order savedOrder = orderService.createOrder(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedOrder);
    }

    @Operation(summary = "Get all orders (Scatter-Gather)", description = "Fetches all orders. Because no userId is provided in this request, ShardingSphere has to execute the query against BOTH databases and merge the results.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved all orders from all shards")
    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }
    
    @Operation(summary = "Get a specific order", description = "Fetches an order. Providing the userId prevents a scatter-gather and routes the query directly to the correct database shard.")
    @ApiResponse(responseCode = "200", description = "Found the order")
    @ApiResponse(responseCode = "404", description = "Order not found in the shard")
    @GetMapping("/{orderId}/users/{userId}")
    public ResponseEntity<Order> getOrder(
            @Parameter(description = "The ID of the order to fetch", example = "1001") @PathVariable Long orderId, 
            @Parameter(description = "The Shard Key (User ID)", example = "100") @PathVariable Long userId) {
        return orderService.getOrderByIdAndUserId(orderId, userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Update order status", description = "Updates the status of an existing order. Requires the Shard Key for optimal routing.")
    @ApiResponse(responseCode = "200", description = "Order updated successfully")
    @ApiResponse(responseCode = "404", description = "Order not found")
    @PutMapping("/{orderId}/users/{userId}/status")
    public ResponseEntity<Void> updateStatus(
            @Parameter(description = "The ID of the order to update", example = "1001") @PathVariable Long orderId, 
            @Parameter(description = "The Shard Key (User ID)", example = "100") @PathVariable Long userId, 
            @Parameter(description = "The new status", example = "SHIPPED") @RequestParam String status) {
        orderService.updateOrderStatus(orderId, userId, status);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Delete an order", description = "Deletes an order from the specific shard.")
    @ApiResponse(responseCode = "204", description = "Order deleted successfully")
    @ApiResponse(responseCode = "404", description = "Order not found")
    @DeleteMapping("/{orderId}/users/{userId}")
    public ResponseEntity<Void> deleteOrder(
            @Parameter(description = "The ID of the order to delete", example = "1001") @PathVariable Long orderId, 
            @Parameter(description = "The Shard Key (User ID)", example = "100") @PathVariable Long userId) {
        orderService.deleteOrder(orderId, userId);
        return ResponseEntity.noContent().build();
    }
}
