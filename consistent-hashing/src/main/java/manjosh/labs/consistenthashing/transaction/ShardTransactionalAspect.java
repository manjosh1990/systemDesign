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

import java.util.Map;

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
 */
@Aspect
@Component
public class ShardTransactionalAspect {

    private static final Logger log = LoggerFactory.getLogger(ShardTransactionalAspect.class);

    private final ConsistentHashRing ring;
    private final Map<String, EntityManagerFactory> emfMap;

    // SpEL tools for evaluating the routingKey dynamically
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public ShardTransactionalAspect(ConsistentHashRing ring, Map<String, EntityManagerFactory> emfMap) {
        this.ring = ring;
        this.emfMap = emfMap;
    }

    @Around("@annotation(shardTransactional)")
    public Object manageTransaction(ProceedingJoinPoint pjp, ShardTransactional shardTransactional) throws Throwable {

        // 1. Evaluate the SpEL routing key (e.g., "#order.userId" -> "42")
        String routingKeyStr = evaluateSpel(pjp, shardTransactional.routingKey());

        // 2. Ask the ring which shard owns this key
        String shardName = ring.getNode(routingKeyStr);
        log.debug("Routing key '{}' maps to shard: {}", routingKeyStr, shardName);

        // 3. Get the correct EntityManagerFactory for that shard
        EntityManagerFactory emf = emfMap.get(shardName);
        if (emf == null) {
            throw new IllegalStateException("No EntityManagerFactory configured for shard: " + shardName);
        }

        // 4. Create EntityManager and start transaction
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        
        try {
            tx.begin();
            
            // 5. Bind EM to the current thread so the service method can access it
            ShardEntityManagerHolder.set(em);

            // 6. Execute the actual service method
            Object result = pjp.proceed();

            // 7. Commit on success
            tx.commit();
            log.debug("Transaction committed on shard: {}", shardName);
            
            return result;

        } catch (Throwable ex) {
            // 8. Rollback on exception
            if (tx.isActive()) {
                tx.rollback();
                log.debug("Transaction rolled back on shard: {} due to {}", shardName, ex.getMessage());
            }
            throw ex;
        } finally {
            // 9. Always clean up
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
