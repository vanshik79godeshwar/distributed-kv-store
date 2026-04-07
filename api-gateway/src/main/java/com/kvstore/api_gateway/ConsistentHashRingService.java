package com.kvstore.api_gateway;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Consistent Hash Ring — routes a key to the correct primary node.
 *
 * Algorithm:
 *   For every real node URL, {@code virtualNodeCount} virtual nodes are
 *   placed on a 32-bit ring using MD5 hashes.  A key is mapped to the
 *   first virtual node whose ring position is ≥ the key's hash
 *   (clockwise look-up on the ring).  This minimises key remapping when
 *   nodes are added or removed.
 *
 * Usage:
 *   String targetNodeBaseUrl = hashRingService.getNodeForKey("user_1");
 *   // → "http://localhost:8081"
 */
@Service
public class ConsistentHashRingService {

    private final List<String> primaryNodeUrls;
    private final int virtualNodeCount;

    /** The ring: hash position → real node base URL */
    private final SortedMap<Long, String> ring = new TreeMap<>();

    public ConsistentHashRingService(List<String> primaryNodeUrls,
                                     int virtualNodeCount) {
        this.primaryNodeUrls = primaryNodeUrls;
        this.virtualNodeCount = virtualNodeCount;
    }

    /** Builds the ring once at application startup. */
    @PostConstruct
    public void buildRing() {
        ring.clear();
        for (String nodeUrl : primaryNodeUrls) {
            for (int i = 0; i < virtualNodeCount; i++) {
                // Each virtual node gets a unique label: "<nodeUrl>-vnode-<i>"
                long hash = hash(nodeUrl + "-vnode-" + i);
                ring.put(hash, nodeUrl);
            }
        }
        System.out.printf(
            "[API-GATEWAY] Hash ring built with %d real node(s), %d virtual nodes each (%d total slots).%n",
            primaryNodeUrls.size(), virtualNodeCount, ring.size()
        );
    }

    /**
     * Returns the base URL of the primary node responsible for {@code key}.
     *
     * @param key the KV-store key to route.
     * @return the base URL, e.g. {@code "http://localhost:8081"}.
     * @throws IllegalStateException if no nodes are registered.
     */
    public String getNodeForKey(String key) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("Consistent hash ring is empty — no primary nodes configured.");
        }

        long keyHash = hash(key);

        // Find the first ring position ≥ keyHash (clockwise look-up).
        SortedMap<Long, String> tail = ring.tailMap(keyHash);

        // If no position is ≥ keyHash, wrap around to the start of the ring.
        String nodeUrl = tail.isEmpty() ? ring.get(ring.firstKey()) : ring.get(tail.firstKey());

        System.out.printf("[API-GATEWAY] key='%s' hash=%d → %s%n", key, keyHash, nodeUrl);
        return nodeUrl;
    }

    // ----------------------------------------------------------------
    // Internal helpers
    // ----------------------------------------------------------------

    /**
     * Produces a stable, non-negative 32-bit hash of the given label using
     * the first 4 bytes of an MD5 digest.  MD5 is used here purely as a
     * uniform, deterministic hash function — not for any security purpose.
     */
    private long hash(String label) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(label.getBytes(StandardCharsets.UTF_8));
            // Build a 32-bit unsigned value from the first 4 bytes.
            long h = 0;
            for (int i = 0; i < 4; i++) {
                h = (h << 8) | (digest[i] & 0xFFL);
            }
            return h;
        } catch (NoSuchAlgorithmException e) {
            // MD5 is mandated by the Java spec — this should never happen.
            throw new RuntimeException("MD5 algorithm unavailable", e);
        }
    }
}
