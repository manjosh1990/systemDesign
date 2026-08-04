package manjosh.labs.consistenthashing.service;

import manjosh.labs.consistenthashing.entity.Order;

import java.util.List;

public interface OrderService {
    
    Order createOrder(Order order);
    
    Order getOrder(Long userId, Long orderId);

    List<Order> getOrdersByUserId(Long userId);
    
    void updateOrderStatus(Long userId, Long orderId, String newStatus);
    
    void deleteOrder(Long userId, Long orderId);
}
