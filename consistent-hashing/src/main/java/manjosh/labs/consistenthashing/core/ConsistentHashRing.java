package manjosh.labs.consistenthashing.core;

import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * ConsistentHashRing — the core data structure.
 *
 * Concept:
 *   - Imagine a circle (ring) numbered 0 → 2^32.
 *   - Both nodes and keys are "placed" on this ring via a hash function.
 *   - To find which node owns a key: walk CLOCKWISE from the key's position
 *     until you hit a node. That node owns the key.
 *
 * Virtual Nodes (vnodes):
 *   - A single node placed at one point on the ring causes uneven load.
 *   - Instead, each physical node gets VIRTUAL_NODES positions on the ring.
 *   - This spreads the load evenly across all physical nodes.
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

    // -------------------------------------------------------------------------
    // Node Management
    // -------------------------------------------------------------------------

    /**
     * Add a physical node to the ring.
     * Creates VIRTUAL_NODES entries like: hash("shard-0#0"), hash("shard-0#1"), ...
     */
    public synchronized void addNode(String nodeName) {
        for (int i = 0; i < VIRTUAL_NODES; i++) {
            // Each virtual node gets a unique key: "nodeName#i"
            long position = hash(nodeName + "#" + i);
            ring.put(position, nodeName);
        }
    }

    /**
     * Remove a physical node from the ring.
     * Removes all its virtual node entries.
     */
    public synchronized void removeNode(String nodeName) {
        for (int i = 0; i < VIRTUAL_NODES; i++) {
            long position = hash(nodeName + "#" + i);
            ring.remove(position);
        }
    }

    // -------------------------------------------------------------------------
    // Key Lookup — the clockwise walk
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
     */
    public synchronized String getNode(String key) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("Hash ring is empty — add nodes first.");
        }

        long position = hash(key);

        // tailMap returns all entries with key >= position (clockwise portion)
        SortedMap<Long, String> tail = ring.tailMap(position);

        // Wrap around if we're past the last node on the ring
        long nodePosition = tail.isEmpty() ? ring.firstKey() : tail.firstKey();

        return ring.get(nodePosition);
    }

    /**
     * Returns an unmodifiable snapshot of the ring for inspection/debugging.
     */
    public SortedMap<Long, String> getRingSnapshot() {
        return Collections.unmodifiableSortedMap(ring);
    }

    public int getNodeCount() {
        // Distinct physical nodes = distinct values in the ring
        return (int) ring.values().stream().distinct().count();
    }

    // -------------------------------------------------------------------------
    // Hash Function — MD5 → first 8 bytes → Long
    // -------------------------------------------------------------------------

    /**
     * Hash a string to a Long position on the ring using MD5.
     *
     * Why MD5 here?
     *   - We don't need cryptographic security — just uniform distribution.
     *   - MD5 gives 128 bits; we take the first 8 bytes (64 bits) for a Long.
     *   - This gives 2^64 positions — far more than enough for a demo ring.
     */
    private long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes());

            // Combine first 8 bytes into a long (big-endian)
            long hash = 0;
            for (int i = 0; i < 8; i++) {
                hash = (hash << 8) | (digest[i] & 0xFF);
            }
            return hash;
        } catch (NoSuchAlgorithmException e) {
            // MD5 is guaranteed by the Java spec — this will never happen
            throw new RuntimeException("MD5 not available", e);
        }
    }
}
