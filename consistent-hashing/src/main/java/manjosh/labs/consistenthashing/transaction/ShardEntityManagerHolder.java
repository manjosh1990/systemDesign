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
 * Nesting protection:
 *   If method A (@ShardTransactional) calls method B (@ShardTransactional),
 *   the depth counter ensures:
 *     - Method B reuses the SAME EntityManager (doesn't create a new one)
 *     - clear() only removes the EM when depth reaches zero (outermost call)
 *
 * Lifecycle (managed entirely by ShardTransactionalAspect):
 *   1. Aspect calls set(em)         ← stores it, increments depth
 *   2. Service method calls get()   ← reads it from this thread's slot
 *   3. Aspect calls clear()         ← decrements depth, removes only at 0
 */
public class ShardEntityManagerHolder {

    private static final ThreadLocal<EntityManager> holder = new ThreadLocal<>();
    private static final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);

    /**
     * Stores the EntityManager for the current thread and increments nesting depth.
     * Called by ShardTransactionalAspect before the method executes.
     */
    public static void set(EntityManager em) {
        holder.set(em);
        depth.set(depth.get() + 1);
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
     * Returns true if an EntityManager is already bound to this thread.
     * Used by the aspect to detect nested @ShardTransactional calls.
     */
    public static boolean isActive() {
        return holder.get() != null;
    }

    /**
     * Decrements nesting depth. Removes the EntityManager only when the outermost
     * @ShardTransactional method completes (depth reaches 0).
     *
     * This prevents a nested call from clearing the EM that the outer call still needs.
     */
    public static void clear() {
        int current = depth.get() - 1;
        if (current <= 0) {
            holder.remove();
            depth.remove();
        } else {
            depth.set(current);
        }
    }
}
