package manjosh.labs.consistenthashing.service;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import manjosh.labs.consistenthashing.config.ShardProperties;
import manjosh.labs.consistenthashing.config.ShardRegistry;
import manjosh.labs.consistenthashing.core.ConsistentHashRing;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

@Service
public class ShardManagementService {

    private static final Logger log = LoggerFactory.getLogger(ShardManagementService.class);

    private final ShardProperties shardProperties;
    private final ShardRegistry shardRegistry;
    private final ConsistentHashRing ring;

    public ShardManagementService(ShardProperties shardProperties, ShardRegistry shardRegistry, ConsistentHashRing ring) {
        this.shardProperties = shardProperties;
        this.shardRegistry = shardRegistry;
        this.ring = ring;
    }

    @PostConstruct
    public void bootstrap() {
        log.info("Bootstrapping shards from master metadata DB...");
        ShardProperties.Master master = shardProperties.getMaster();
        if (master == null || master.getUrl() == null) {
            throw new IllegalStateException("Master database config missing in application.properties");
        }

        try (Connection conn = DriverManager.getConnection(master.getUrl(), master.getUsername(), master.getPassword())) {
            // 1. Ensure registry table exists
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS shard_registry (" +
                        "shard_name VARCHAR(50) PRIMARY KEY, " +
                        "url VARCHAR(255) NOT NULL, " +
                        "username VARCHAR(100) NOT NULL, " +
                        "password VARCHAR(100) NOT NULL" +
                        ")");
            }

            // 2. Check if empty. If yes, register ds0 (master itself) as the first data shard
            boolean isEmpty;
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM shard_registry")) {
                rs.next();
                isEmpty = rs.getInt(1) == 0;
            }

            if (isEmpty) {
                log.info("Shard registry is empty. Auto-registering ds0 as the primary shard.");
                try (PreparedStatement pstmt = conn.prepareStatement(
                        "INSERT INTO shard_registry (shard_name, url, username, password) VALUES (?, ?, ?, ?)")) {
                    pstmt.setString(1, "ds0");
                    pstmt.setString(2, master.getUrl());
                    pstmt.setString(3, master.getUsername());
                    pstmt.setString(4, master.getPassword());
                    pstmt.executeUpdate();
                }
            }

            // 3. Load all shards and bootstrap them into memory
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM shard_registry")) {
                while (rs.next()) {
                    String name = rs.getString("shard_name");
                    String url = rs.getString("url");
                    String user = rs.getString("username");
                    String pass = rs.getString("password");
                    
                    log.info("Bootstrapping shard: {}", name);
                    addShardToMemory(name, url, user, pass);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to bootstrap shards from metadata db", e);
        }
    }

    /**
     * Called via REST API to add a new shard dynamically at runtime.
     * It provisions the shard in memory first, and if successful, saves it to the DB.
     */
    public void registerNewShard(String shardName, String url, String username, String password) {
        log.info("Registering NEW shard dynamically: {}", shardName);
        
        // 1. Provision in memory first (tests connection, runs Flyway, builds EMF, adds to Ring)
        addShardToMemory(shardName, url, username, password);

        // 2. Once successfully provisioned, persist to the master metadata registry
        ShardProperties.Master master = shardProperties.getMaster();
        try (Connection conn = DriverManager.getConnection(master.getUrl(), master.getUsername(), master.getPassword());
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO shard_registry (shard_name, url, username, password) VALUES (?, ?, ?, ?)")) {
            pstmt.setString(1, shardName);
            pstmt.setString(2, url);
            pstmt.setString(3, username);
            pstmt.setString(4, password);
            pstmt.executeUpdate();
            log.info("Successfully persisted {} to the master registry", shardName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save new shard to registry", e);
        }
    }

    private void addShardToMemory(String shardName, String url, String username, String password) {
        // 1. Create Connection Pool
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setMaximumPoolSize(10);
        ds.setPoolName("pool-" + shardName);

        // 2. Run Flyway Migrations (creates t_order table)
        log.info("Running Flyway for shard: {}", shardName);
        Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();

        // 3. Create EntityManagerFactory
        LocalContainerEntityManagerFactoryBean factoryBean = new LocalContainerEntityManagerFactoryBean();
        factoryBean.setDataSource(ds);
        factoryBean.setPackagesToScan("manjosh.labs.consistenthashing.entity");
        factoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factoryBean.setPersistenceUnitName(shardName);
        
        Properties jpaProps = new Properties();
        jpaProps.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        jpaProps.setProperty("hibernate.show_sql", "true");
        jpaProps.setProperty("hibernate.hbm2ddl.auto", "none");
        jpaProps.setProperty("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        factoryBean.setJpaProperties(jpaProps);
        factoryBean.afterPropertiesSet();
        EntityManagerFactory emf = factoryBean.getObject();

        // 4. Register in memory
        shardRegistry.registerDataSource(shardName, ds);
        shardRegistry.registerEntityManagerFactory(shardName, emf);
        ring.addNode(shardName);
        log.info("Successfully added {} to the Consistent Hash Ring!", shardName);
    }
}
