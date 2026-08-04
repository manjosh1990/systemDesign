package manjosh.labs.consistenthashing.core;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * ConsistentHashRing — the core data structure.
 *
 * Concept:
 *   - Imagine a circle (ring) numbered 0 → 2^64.
 *   - Both nodes and keys are "placed" on this ring via a hash function.
 *   - To find which node owns a key: walk CLOCKWISE from the key's position
 *     until you hit a node. That node owns the key.
 *
 * Virtual Nodes (vnodes):
 *   - A single node placed at one point on the ring causes uneven load.
 *   - Instead, each physical node gets VIRTUAL_NODES positions on the ring.
 *   - This spreads the load evenly across all physical nodes.
 *
 * Concurrency model:
 *   - ReadWriteLock allows multiple concurrent readers (getNode — hot path)
 *   - Writers (addNode/removeNode — cold path) get exclusive access
 *   - Readers never block each other; only a writer blocks readers
 *
 * Internal structure:
 *   TreeMap<Long, String>
 *     key   = hash position on the ring (Long)
 *     value = physical node name (e.g. "shard-0")
 */
@Component
public class ConsistentHashRing {

    // Each physical node is replicated this many times on the ring.
    // Higher = more uniform distribution, more memory.
    private static final int VIRTUAL_NODES = 150;

    // The ring: sorted map of (ring-position → node-name)
    // TreeMap keeps entries in ascending key order — perfect for clockwise lookup.
    private final TreeMap<Long, String> ring = new TreeMap<>();

    // ReadWriteLock: multiple readers concurrently, exclusive writer
    // This is the key difference from synchronized — getNode() no longer serializes requests.
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    // -------------------------------------------------------------------------
    // Node Management (write path — rare, exclusive)
    // -------------------------------------------------------------------------

    /**
     * Add a physical node to the ring.
     * Creates VIRTUAL_NODES entries like: hash("shard-0#0"), hash("shard-0#1"), ...
     *
     * Acquires write lock — blocks until all readers finish, then blocks all new readers.
     * This guarantees readers always see a complete set of vnodes (all 150 or none).
     */
    public void addNode(String nodeName) {
        lock.writeLock().lock();
        try {
            for (int i = 0; i < VIRTUAL_NODES; i++) {
                ring.put(hash(nodeName + "#" + i), nodeName);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Remove a physical node from the ring.
     * Removes all its virtual node entries.
     */
    public void removeNode(String nodeName) {
        lock.writeLock().lock();
        try {
            for (int i = 0; i < VIRTUAL_NODES; i++) {
                ring.remove(hash(nodeName + "#" + i));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    // -------------------------------------------------------------------------
    // Key Lookup — the clockwise walk (read path — hot, concurrent)
    // -------------------------------------------------------------------------

    /**
     * Given a key (e.g. "user:42"), find which node owns it.
     *
     * Algorithm:
     *   1. Hash the key to get its position on the ring.
     *   2. Use TreeMap.tailMap(position) to get all ring entries AT or AFTER that position.
     *   3. If tailMap is empty, we've gone past the end of the ring → wrap around
     *      and take ring.firstKey() (the node at the "start" of the ring).
     *   4. Return the node at the first entry found.
     *
     * Acquires read lock — multiple threads can execute this simultaneously.
     * Only blocked when a writer (addNode/removeNode) is in progress.
     */
    public String getNode(String key) {
        lock.readLock().lock();
        try {
            if (ring.isEmpty()) {
                throw new IllegalStateException("Hash ring is empty — add nodes first.");
            }

            long position = hash(key);

            // tailMap returns all entries with key >= position (clockwise portion)
            SortedMap<Long, String> tail = ring.tailMap(position);

            // Wrap around if we're past the last node on the ring
            long nodePosition = tail.isEmpty() ? ring.firstKey() : tail.firstKey();

            return ring.get(nodePosition);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns a defensive copy of the ring for inspection/debugging.
     */
    public SortedMap<Long, String> getRingSnapshot() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableSortedMap(new TreeMap<>(ring));
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getNodeCount() {
        lock.readLock().lock();
        try {
            return (int) ring.values().stream().distinct().count();
        } finally {
            lock.readLock().unlock();
        }
    }

    // -------------------------------------------------------------------------
    // Hash Function — MurmurHash3-inspired (fast, good distribution)
    // -------------------------------------------------------------------------

    /**
     * Hash a string to a Long position on the ring.
     *
     * Why not String.hashCode()?
     *   - String.hashCode() is 32-bit with weak avalanche for short strings.
     *   - "1", "2", "3" produce sequential hash codes — poor ring spread.
     *
     * Why not MD5?
     *   - MD5 allocates MessageDigest + byte[] per call (~200ns).
     *   - We don't need cryptographic properties — just uniform distribution.
     *
     * Approach: FNV-1a over the string bytes, then MurmurHash3 finalization mix.
     *   - FNV-1a: simple, fast, processes all bytes (not just first few like hashCode).
     *   - Murmur mix: ensures excellent avalanche (1-bit input change → ~50% output bits flip).
     */
    private long hash(String key) {
        long h = fnv1a64(key);
        return murmur3Mix64(h);
    }

    /**
     * FNV-1a 64-bit hash — processes every byte of the input.
     * Simple loop, no allocations, good base distribution.
     */
    private static long fnv1a64(String key) {
        long hash = 0xcbf29ce484222325L; // FNV offset basis
        for (int i = 0; i < key.length(); i++) {
            hash ^= key.charAt(i);
            hash *= 0x100000001b3L;       // FNV prime
        }
        return hash;
    }

    /**
     * MurmurHash3 64-bit finalizer (fmix64).
     * Takes any long and spreads its bits uniformly across the output space.
     */
    private static long murmur3Mix64(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;
        return k;
    }
}
