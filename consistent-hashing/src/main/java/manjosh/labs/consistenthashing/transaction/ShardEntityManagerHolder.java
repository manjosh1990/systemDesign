package manjosh.labs.consistenthashing.transaction;

import jakarta.persistence.EntityManager;

/**
 * ShardEntityManagerHolder — stores the current request's EntityManager in a ThreadLocal.
 *
 * Why ThreadLocal?
 *   Each HTTP request runs on its own thread.
 *   ThreadLocal gives each thread its own private storage slot.
 *   So two concurrent requests don't share or overwrite each other's EntityManager.
 *
 * Lifecycle (managed entirely by ShardTransactionalAspect):
 *   1. Aspect creates EntityManager for the correct shard
 *   2. Aspect calls set(em)         ← stores it for this thread
 *   3. Service method calls get()   ← reads it from this thread's slot
 *   4. Aspect calls clear()         ← removes it after commit/rollback
 *
 * The service method never creates an EntityManager itself.
 * It just reads from this holder — which keeps service code clean.
 */
public class ShardEntityManagerHolder {

    private static final ThreadLocal<EntityManager> holder = new ThreadLocal<>();

    /**
     * Stores the EntityManager for the current thread.
     * Called by ShardTransactionalAspect before the method executes.
     */
    public static void set(EntityManager em) {
        holder.set(em);
    }

    /**
     * Returns the EntityManager for the current thread.
     * Called by service methods to get the JPA session for the routed shard.
     */
    public static EntityManager get() {
        EntityManager em = holder.get();
        if (em == null) {
            throw new IllegalStateException(
                "No EntityManager found for current thread. " +
                "Make sure the method is annotated with @ShardTransactional."
            );
        }
        return em;
    }

    /**
     * Removes the EntityManager for the current thread.
     * Called by ShardTransactionalAspect after commit/rollback to prevent memory leaks.
     *
     * ThreadLocal values are not garbage collected automatically when the thread
     * returns to a thread pool — always clear after use.
     */
    public static void clear() {
        holder.remove();
    }
}
