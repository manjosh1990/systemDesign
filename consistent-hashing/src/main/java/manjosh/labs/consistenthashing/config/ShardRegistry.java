package manjosh.labs.consistenthashing.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A central, thread-safe registry holding all active database connections and JPA sessions.
 * Modified dynamically by ShardManagementService at runtime.
 */
@Component
public class ShardRegistry {
    private final Map<String, DataSource> dataSourceMap = new ConcurrentHashMap<>();
    private final Map<String, EntityManagerFactory> emfMap = new ConcurrentHashMap<>();

    public void registerDataSource(String shardName, DataSource ds) {
        dataSourceMap.put(shardName, ds);
    }

    public void registerEntityManagerFactory(String shardName, EntityManagerFactory emf) {
        emfMap.put(shardName, emf);
    }

    public DataSource getDataSource(String shardName) {
        return dataSourceMap.get(shardName);
    }

    public EntityManagerFactory getEntityManagerFactory(String shardName) {
        return emfMap.get(shardName);
    }

    public Map<String, DataSource> getAllDataSources() {
        return dataSourceMap;
    }

    public Map<String, EntityManagerFactory> getAllEntityManagerFactories() {
        return emfMap;
    }
}
