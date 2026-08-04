package manjosh.labs.consistenthashing.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import manjosh.labs.consistenthashing.config.ShardRegistry;
import manjosh.labs.consistenthashing.core.ConsistentHashRing;
import manjosh.labs.consistenthashing.entity.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DataRebalancerService — Migrates stranded data to its correct shard.
 * 
 * In a real-world system like Cassandra, rebalancing happens continuously in the background
 * using SSTables and Merkle trees. Here, we do a simplistic table scan for demonstration.
 */
@Service
public class DataRebalancerService {

    private static final Logger log = LoggerFactory.getLogger(DataRebalancerService.class);

    private final ShardRegistry shardRegistry;
    private final ConsistentHashRing ring;

    public DataRebalancerService(ShardRegistry shardRegistry, ConsistentHashRing ring) {
        this.shardRegistry = shardRegistry;
        this.ring = ring;
    }

    public int rebalanceData() {
        log.info("Starting cluster-wide data rebalance...");
        AtomicInteger movedRecords = new AtomicInteger(0);

        Map<String, EntityManagerFactory> emfMap = shardRegistry.getAllEntityManagerFactories();

        // Iterate over every active shard in the cluster
        for (Map.Entry<String, EntityManagerFactory> entry : emfMap.entrySet()) {
            String currentShardName = entry.getKey();
            EntityManagerFactory sourceEmf = entry.getValue();

            EntityManager sourceEm = sourceEmf.createEntityManager();
            try {
                sourceEm.getTransaction().begin();

                // Get all records in this shard (in a real system, you'd paginate this)
                List<Order> orders = sourceEm.createQuery("SELECT o FROM Order o", Order.class).getResultList();

                for (Order order : orders) {
                    // Ask the ring where this order is SUPPOSED to be
                    String targetShardName = ring.getNode(order.getUserId().toString());

                    // If it belongs somewhere else, move it!
                    if (!currentShardName.equals(targetShardName)) {
                        log.info("Migrating Order {} from {} to {}", order.getOrderId(), currentShardName, targetShardName);
                        moveOrder(order, sourceEm, targetShardName);
                        movedRecords.incrementAndGet();
                    }
                }
                sourceEm.getTransaction().commit();
            } catch (Exception e) {
                log.error("Failed to rebalance shard: " + currentShardName, e);
                if (sourceEm.getTransaction().isActive()) {
                    sourceEm.getTransaction().rollback();
                }
            } finally {
                sourceEm.close();
            }
        }

        log.info("Rebalance complete! Moved {} records.", movedRecords.get());
        return movedRecords.get();
    }

    private void moveOrder(Order order, EntityManager sourceEm, String targetShardName) {
        EntityManagerFactory targetEmf = shardRegistry.getEntityManagerFactory(targetShardName);
        if (targetEmf == null) {
            throw new IllegalStateException("Target shard " + targetShardName + " is missing from registry!");
        }

        EntityManager targetEm = targetEmf.createEntityManager();
        try {
            targetEm.getTransaction().begin();

            // Insert into the new target shard
            targetEm.merge(order);
            
            // Delete from the old source shard
            sourceEm.remove(order);

            targetEm.getTransaction().commit();
        } catch (Exception e) {
            targetEm.getTransaction().rollback();
            throw e;
        } finally {
            targetEm.close();
        }
    }
}
