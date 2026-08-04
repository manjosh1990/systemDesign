package manjosh.labs.consistenthashing.controller;

import manjosh.labs.consistenthashing.entity.Order;
import manjosh.labs.consistenthashing.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody Order order) {
        Order savedOrder = orderService.createOrder(order);
        return ResponseEntity.ok(savedOrder);
    }

    @GetMapping("/{userId}/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable Long userId, @PathVariable Long orderId) {
        Order order = orderService.getOrder(userId, orderId);
        return ResponseEntity.ok(order);
    }

    @PutMapping("/{userId}/{orderId}/status")
    public ResponseEntity<Void> updateOrderStatus(
            @PathVariable Long userId, 
            @PathVariable Long orderId, 
            @RequestParam String status) {
        orderService.updateOrderStatus(userId, orderId, status);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{userId}/{orderId}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long userId, @PathVariable Long orderId) {
        orderService.deleteOrder(userId, orderId);
        return ResponseEntity.noContent().build();
    }
}
