package manjosh.labs.consistenthashing.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Order Entity — represents a row in the t_order table.
 *
 * This matches the exact same schema as the sharding project POC.
 * We map it manually to "t_order".
 */
@Entity
@Table(name = "t_order")
public class Order {

    @Id
    private Long orderId;

    private Long userId;

    private String status;

    // Constructors
    public Order() {
    }

    public Order(Long orderId, Long userId, String status) {
        this.orderId = orderId;
        this.userId = userId;
        this.status = status;
    }

    // Getters and Setters
    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
