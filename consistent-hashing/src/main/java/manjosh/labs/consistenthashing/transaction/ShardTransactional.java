package manjosh.labs.consistenthashing.transaction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @ShardTransactional — marks a service method that needs a JPA transaction
 * routed to the correct shard via ConsistentHashRing.
 *
 * Usage:
 *   @ShardTransactional(routingKey = "#order.userId")
 *   public Order createOrder(Order order) { ... }
 *
 * How it works:
 *   The ShardTransactionalAspect intercepts this method and:
 *     1. Evaluates the SpEL expression in routingKey against the method args
 *     2. Calls ring.getNode(value) → "ds0" or "ds1"
 *     3. Creates an EntityManager for that shard
 *     4. Begins a transaction
 *     5. Stores the EntityManager in ShardEntityManagerHolder (ThreadLocal)
 *     6. Runs the method body (which reads the EM from the ThreadLocal)
 *     7. Commits on success, rollbacks on exception
 *     8. Closes the EntityManager
 *
 * routingKey examples:
 *   "#order.userId"      → extracts order.getUserId() from first arg
 *   "#userId"            → uses the 'userId' parameter directly
 *   "#orderId.toString()" → converts to string if needed
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ShardTransactional {

    /**
     * SpEL expression to extract the routing key from the method arguments.
     * The key is passed to ConsistentHashRing.getNode(key) to determine the shard.
     */
    String routingKey();
}
