package manjosh.labs.consistenthashing.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * DataSourceConfig — Single Responsibility: create one HikariDataSource per shard.
 *
 * That's it. Nothing else.
 *   - Ring seeding   → RingInitializer.java      (@PostConstruct)
 *   - Schema migration → FlywayMigrationRunner.java (@PostConstruct)
 *   - JPA setup      → JpaConfig.java            (@Bean)
 *
 * Output: Map<"ds0", DataSource>, Map<"ds1", DataSource>
 * Consumed by: JpaConfig, RingInitializer, FlywayMigrationRunner
 */
@Configuration
public class DataSourceConfig {

    private final ShardProperties shardProperties;

    public DataSourceConfig(ShardProperties shardProperties) {
        this.shardProperties = shardProperties;
    }

    @Bean
    public Map<String, DataSource> dataSourceMap() {
        Map<String, DataSource> map = new HashMap<>();

        for (ShardProperties.Shard shard : shardProperties.getShards()) {
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(shard.getUrl());
            ds.setUsername(shard.getUsername());
            ds.setPassword(shard.getPassword());
            ds.setMaximumPoolSize(10);
            ds.setPoolName("pool-" + shard.getName()); // e.g. "pool-ds0"

            map.put(shard.getName(), ds); // "ds0" → connection pool for ds_0
        }

        return map;
    }
}
