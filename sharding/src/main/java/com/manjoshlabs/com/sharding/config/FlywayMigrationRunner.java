package com.manjoshlabs.com.sharding.config;

import org.flywaydb.core.Flyway;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

@Component
public class FlywayMigrationRunner {

    private final FlywayMigrationProperties properties;

    public FlywayMigrationRunner(FlywayMigrationProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void migrate() {
        if (properties.getShards() == null || properties.getShards().isEmpty()) {
            System.out.println("No shards configured for migration.");
            return;
        }
        for (FlywayMigrationProperties.ShardDataSource shard : properties.getShards()) {
            System.out.println("===========================================");
            System.out.println("Running Flyway migration for: " + shard.getName());
            System.out.println("===========================================");
            
            Flyway flyway = Flyway.configure()
                    .dataSource(shard.getUrl(), shard.getUsername(), shard.getPassword())
                    .locations("classpath:db/migration")
                    .load();
            
            flyway.migrate();
        }
    }
}
