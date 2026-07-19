package com.manjoshlabs.com.sharding.service;

import com.manjoshlabs.com.sharding.entity.Order;
import com.manjoshlabs.com.sharding.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;

    public OrderServiceImpl(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public Order createOrder(Order order) {
        return orderRepository.save(order);
    }

    @Override
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    @Override
    public Optional<Order> getOrderByIdAndUserId(Long orderId, Long userId) {
        return orderRepository.findByOrderIdAndUserId(orderId, userId);
    }

    @Override
    public void updateOrderStatus(Long orderId, Long userId, String newStatus) {
        if (orderRepository.findByOrderIdAndUserId(orderId, userId).isEmpty()) {
            throw new com.manjoshlabs.com.sharding.exception.OrderNotFoundException("Order " + orderId + " not found for user " + userId);
        }
        orderRepository.updateStatusByOrderIdAndUserId(orderId, userId, newStatus);
    }

    @Override
    public void deleteOrder(Long orderId, Long userId) {
        if (orderRepository.findByOrderIdAndUserId(orderId, userId).isEmpty()) {
            throw new com.manjoshlabs.com.sharding.exception.OrderNotFoundException("Order " + orderId + " not found for user " + userId);
        }
        orderRepository.deleteByOrderIdAndUserId(orderId, userId);
    }
}
