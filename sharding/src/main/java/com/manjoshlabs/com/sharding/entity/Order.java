package com.manjoshlabs.com.sharding.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Table(name = "t_order")
@Schema(description = "An order entity representing a customer's purchase")
public class Order {

    @Id
    @Schema(description = "The unique identifier of the order", example = "1001")
    private Long orderId;

    @Schema(description = "The ID of the user. This acts as the Shard Key to determine which physical database the order is stored in (user_id % 2).", example = "100")
    private Long userId; 

    @Schema(description = "The current status of the order (e.g., NEW, SHIPPED, DELIVERED)", example = "NEW")
    private String status;

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
