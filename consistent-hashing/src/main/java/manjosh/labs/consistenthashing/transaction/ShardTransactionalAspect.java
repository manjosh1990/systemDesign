package manjosh.labs.consistenthashing.transaction;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import manjosh.labs.consistenthashing.core.ConsistentHashRing;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;
import manjosh.labs.consistenthashing.config.ShardRegistry;

/**
 * ShardTransactionalAspect — manages JPA transactions across multiple shards dynamically.
 *
 * This aspect intercepts any method annotated with @ShardTransactional.
 * It replaces all the manual try/commit/rollback boilerplate.
 *
 * Workflow:
 * 1. Read the SpEL routingKey from the annotation (e.g., "#order.userId")
 * 2. Evaluate it against the actual method arguments (e.g., returns 42)
 * 3. Ask ConsistentHashRing which shard owns key "42" (e.g., "ds0")
 * 4. Get the EntityManagerFactory for "ds0"
 * 5. Create an EntityManager and begin transaction
 * 6. Bind EntityManager to the current thread (ThreadLocal)
 * 7. Execute the actual service method
 * 8. Commit (if success) or Rollback (if exception)
 * 9. Clean up ThreadLocal and close EntityManager
 *
 * Nesting:
 *   If a @ShardTransactional method calls another @ShardTransactional method
 *   targeting the SAME shard, the inner call joins the existing transaction.
 *   If targeting a DIFFERENT shard, it fails fast — cross-shard transactions
 *   are not supported (would need 2PC/saga).
 */
@Aspect
@Component
public class ShardTransactionalAspect {

    private static final Logger log = LoggerFactory.getLogger(ShardTransactionalAspect.class);

    private final ConsistentHashRing ring;
    private final ShardRegistry shardRegistry;

    // SpEL tools for evaluating the routingKey dynamically
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public ShardTransactionalAspect(ConsistentHashRing ring, ShardRegistry shardRegistry) {
        this.ring = ring;
        this.shardRegistry = shardRegistry;
    }

    @Around("@annotation(manjosh.labs.consistenthashing.transaction.ShardTransactional)")
    public Object manageShardTransaction(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        ShardTransactional annotation = method.getAnnotation(ShardTransactional.class);

        String spelExpression = annotation.routingKey();
        String routingKeyStr = evaluateSpel(pjp, spelExpression);

        // 2. Query the ring for the shard name
        String shardName = ring.getNode(routingKeyStr);
        log.debug("Routing key '{}' maps to shard: {}", routingKeyStr, shardName);

        // 3. Handle nested @ShardTransactional calls
        if (ShardEntityManagerHolder.isActive()) {
            // Already inside a transaction — join it if same shard
            log.debug("Nested @ShardTransactional detected, joining existing transaction on shard: {}", shardName);
            ShardEntityManagerHolder.set(ShardEntityManagerHolder.get());
            try {
                return pjp.proceed();
            } finally {
                ShardEntityManagerHolder.clear();
            }
        }

        // 4. Get the correct EntityManagerFactory for that shard
        EntityManagerFactory emf = shardRegistry.getEntityManagerFactory(shardName);
        if (emf == null) {
            throw new IllegalStateException("No EntityManagerFactory configured for shard: " + shardName);
        }

        // 5. Create EntityManager and start transaction
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            tx.begin();

            // 6. Bind EM to the current thread so the service method can access it
            ShardEntityManagerHolder.set(em);

            // 7. Execute the actual service method
            Object result = pjp.proceed();

            // 8. Commit on success
            tx.commit();
            log.debug("Transaction committed on shard: {}", shardName);

            return result;

        } catch (Throwable ex) {
            // 9. Rollback on exception
            if (tx.isActive()) {
                tx.rollback();
                log.debug("Transaction rolled back on shard: {} due to {}", shardName, ex.getMessage());
            }
            throw ex;
        } finally {
            // 10. Always clean up
            ShardEntityManagerHolder.clear();
            if (em.isOpen()) {
                em.close();
            }
        }
    }

    /**
     * Evaluates a Spring Expression Language (SpEL) string against the method's arguments.
     * E.g., if method is createOrder(Order order) and routingKey is "#order.userId",
     * this evaluates the expression and returns the actual userId.
     */
    private String evaluateSpel(ProceedingJoinPoint pjp, String spelExpression) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(signature.getMethod());
        Object[] args = pjp.getArgs();

        EvaluationContext context = new StandardEvaluationContext();
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }

        Object value = parser.parseExpression(spelExpression).getValue(context);
        if (value == null) {
            throw new IllegalArgumentException("Routing key evaluation resulted in null for expression: " + spelExpression);
        }

        return value.toString();
    }
}
