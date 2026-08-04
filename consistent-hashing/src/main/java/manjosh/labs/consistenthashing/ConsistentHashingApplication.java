package manjosh.labs.consistenthashing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(excludeName = {
    "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
    "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
    "org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration",
    "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
})
public class ConsistentHashingApplication {

	public static void main(String[] args) {
		SpringApplication.run(ConsistentHashingApplication.class, args);
	}
}
