package com.manjoshlabs.com.sharding.repository;

import com.manjoshlabs.com.sharding.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    // Spring Data JPA will automatically generate the SELECT query. 
    // Because userId is in the query, ShardingSphere will route it directly to the specific shard!
    Optional<Order> findByOrderIdAndUserId(Long orderId, Long userId);

    // For Update and Delete, standard JPA might only use the primary key (orderId), 
    // which would cause a scatter-gather. To prevent this, we write explicit custom queries 
    // that include the userId in the WHERE clause.
    @Modifying
    @Transactional
    @Query("DELETE FROM Order o WHERE o.orderId = :orderId AND o.userId = :userId")
    void deleteByOrderIdAndUserId(Long orderId, Long userId);
    
    @Modifying
    @Transactional
    @Query("UPDATE Order o SET o.status = :status WHERE o.orderId = :orderId AND o.userId = :userId")
    void updateStatusByOrderIdAndUserId(Long orderId, Long userId, String status);
}
