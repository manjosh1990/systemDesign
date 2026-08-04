package manjosh.labs.consistenthashing.service;

import manjosh.labs.consistenthashing.entity.Order;

public interface OrderService {
    
    Order createOrder(Order order);
    
    Order getOrder(Long userId, Long orderId);
    
    void updateOrderStatus(Long userId, Long orderId, String newStatus);
    
    void deleteOrder(Long userId, Long orderId);
}
