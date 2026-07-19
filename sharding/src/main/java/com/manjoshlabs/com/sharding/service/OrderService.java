package com.manjoshlabs.com.sharding.service;

import com.manjoshlabs.com.sharding.entity.Order;
import java.util.List;
import java.util.Optional;

public interface OrderService {
    Order createOrder(Order order);
    List<Order> getAllOrders();
    Optional<Order> getOrderByIdAndUserId(Long orderId, Long userId);
    void updateOrderStatus(Long orderId, Long userId, String newStatus);
    void deleteOrder(Long orderId, Long userId);
}
