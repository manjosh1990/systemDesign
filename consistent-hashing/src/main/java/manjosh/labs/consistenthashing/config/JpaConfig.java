package manjosh.labs.consistenthashing.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * JpaConfig — creates one EntityManagerFactory per shard.
 *
 * Why not use Spring Data JPA repositories?
 *   Spring Data JPA repositories are bound to a single EntityManager at compile time.
 *   Our shard is decided at RUNTIME based on the hash of the key.
 *   So we need a Map<String, EntityManagerFactory> and manually pick the right one per request.
 *
 * Flow:
 *   dataSourceMap (from DataSourceConfig) → one EMF per entry → Map<String, EntityManagerFactory>
 *
 * Usage in OrderService:
 *   String node = ring.getNode("user:1");        // → "ds0"
 *   EntityManagerFactory emf = emfMap.get(node); // → EMF for ds_0
 *   EntityManager em = emf.createEntityManager(); // → JPA session on ds_0
 */
@Configuration
public class JpaConfig {

    @Bean
    public Map<String, EntityManagerFactory> entityManagerFactoryMap(
            Map<String, DataSource> dataSourceMap) {

        Map<String, EntityManagerFactory> emfMap = new HashMap<>();

        for (Map.Entry<String, DataSource> entry : dataSourceMap.entrySet()) {
            String shardName = entry.getKey();   // "ds0" or "ds1"
            DataSource dataSource = entry.getValue();

            // LocalContainerEntityManagerFactoryBean is Spring's way to build a JPA EMF
            LocalContainerEntityManagerFactoryBean factoryBean =
                    new LocalContainerEntityManagerFactoryBean();

            factoryBean.setDataSource(dataSource);

            // Scan this package for @Entity classes (Order.java)
            factoryBean.setPackagesToScan("manjosh.labs.consistenthashing.entity");

            // Use Hibernate as the JPA provider
            factoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

            // Give each EMF a unique persistence unit name — required when you have multiple
            factoryBean.setPersistenceUnitName(shardName); // "ds0", "ds1"

            // Hibernate settings
            Properties jpaProps = new Properties();
            jpaProps.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
            jpaProps.setProperty("hibernate.show_sql", "true");
            jpaProps.setProperty("hibernate.hbm2ddl.auto", "none"); // Flyway manages the schema
            factoryBean.setJpaProperties(jpaProps);

            // Must call afterPropertiesSet() manually since we're not using Spring's lifecycle
            factoryBean.afterPropertiesSet();

            emfMap.put(shardName, factoryBean.getObject());
        }

        return emfMap;
    }
}
