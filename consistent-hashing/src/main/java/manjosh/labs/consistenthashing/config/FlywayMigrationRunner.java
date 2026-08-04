package manjosh.labs.consistenthashing.config;

import jakarta.annotation.PostConstruct;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;

/**
 * FlywayMigrationRunner — Single Responsibility: run Flyway migrations on every shard.
 *
 * Runs once at startup (@PostConstruct) after dataSourceMap is ready.
 * Executes db/migration/V1__init_schema.sql on each shard's PostgreSQL instance.
 *
 * Why run Flyway per shard manually?
 *   Spring Boot's auto Flyway runs against a single datasource.
 *   We have two (ds0, ds1) — so we run it ourselves, once per shard.
 *
 * Adding a new shard (ds2):
 *   → add it to application.properties ONLY
 *   → Flyway automatically runs on it — no code change needed (Open/Closed Principle ✅)
 */
@Component
public class FlywayMigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(FlywayMigrationRunner.class);

    private final Map<String, DataSource> dataSourceMap;

    public FlywayMigrationRunner(Map<String, DataSource> dataSourceMap) {
        this.dataSourceMap = dataSourceMap;
    }

    @PostConstruct
    public void migrate() {
        for (Map.Entry<String, DataSource> entry : dataSourceMap.entrySet()) {
            String shardName = entry.getKey();
            DataSource dataSource = entry.getValue();

            log.info("Running Flyway migration for shard: {}", shardName);

            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();
        }
    }
}
