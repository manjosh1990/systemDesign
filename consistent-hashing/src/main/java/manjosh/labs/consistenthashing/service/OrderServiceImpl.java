package manjosh.labs.consistenthashing.service;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import manjosh.labs.consistenthashing.entity.Order;
import manjosh.labs.consistenthashing.exception.OrderNotFoundException;
import manjosh.labs.consistenthashing.transaction.ShardEntityManagerHolder;
import manjosh.labs.consistenthashing.transaction.ShardTransactional;
import org.springframework.stereotype.Service;

/**
 * OrderServiceImpl — implements business logic cleanly without transaction boilerplate.
 *
 * Notice how clean this class is:
 *   - No try/catch/finally
 *   - No tx.begin() or tx.commit()
 *   - No hardcoded routing logic
 *
 * All complexity is handled by @ShardTransactional aspect!
 */
@Service
public class OrderServiceImpl implements OrderService {

    @Override
    @ShardTransactional(routingKey = "#order.userId")
    public Order createOrder(Order order) {
        // The aspect has already resolved the shard using order.userId and set up the EM
        EntityManager em = ShardEntityManagerHolder.get();
        em.persist(order);
        return order;
    }

    @Override
    @ShardTransactional(routingKey = "#userId")
    public Order getOrder(Long userId, Long orderId) {
        EntityManager em = ShardEntityManagerHolder.get();
        Order order = em.find(Order.class, orderId);
        
        if (order == null || !order.getUserId().equals(userId)) {
            throw new OrderNotFoundException("Order " + orderId + " not found for user " + userId);
        }
        
        return order;
    }

    @Override
    @ShardTransactional(routingKey = "#userId")
    public List<Order> getOrdersByUserId(Long userId) {
        EntityManager em = ShardEntityManagerHolder.get();
        return em.createQuery("SELECT o FROM Order o WHERE o.userId = :userId", Order.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    @Override
    @ShardTransactional(routingKey = "#userId")
    public void updateOrderStatus(Long userId, Long orderId, String newStatus) {
        EntityManager em = ShardEntityManagerHolder.get();
        Order order = em.find(Order.class, orderId);
        
        if (order == null || !order.getUserId().equals(userId)) {
            throw new OrderNotFoundException("Order " + orderId + " not found for user " + userId);
        }
        
        order.setStatus(newStatus);
        em.merge(order);
    }

    @Override
    @ShardTransactional(routingKey = "#userId")
    public void deleteOrder(Long userId, Long orderId) {
        EntityManager em = ShardEntityManagerHolder.get();
        Order order = em.find(Order.class, orderId);
        
        if (order != null && order.getUserId().equals(userId)) {
            em.remove(order);
        }
    }
}
