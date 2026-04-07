package com.kvstore.api_gateway;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * GatewayController — the single public-facing entry point of the API Gateway.
 *
 * It exposes the exact three endpoints that the client-simulator calls:
 *
 *   POST   /api/put?key=…&value=…   → stores a key-value pair
 *   GET    /api/get?key=…           → retrieves the value for a key
 *   DELETE /api/delete?key=…        → removes a key
 *
 * Client-simulator sends to: http://localhost:8080/api/{put|get|delete}
 *
 * The controller itself is intentionally thin — all routing intelligence
 * lives in {@link GatewayRoutingService} and {@link ConsistentHashRingService}.
 */
@RestController
@RequestMapping("/api")
public class GatewayController {

    private final GatewayRoutingService routingService;

    public GatewayController(GatewayRoutingService routingService) {
        this.routingService = routingService;
    }

    // ---------------------------------------------------------------
    // PUT  →  POST /api/put?key=…&value=…
    // ---------------------------------------------------------------

    /**
     * Stores a key-value pair in the distributed store.
     *
     * The responsible primary node is determined via the consistent hash ring.
     * The request is forwarded as: {@code POST <primaryNodeUrl>/store?key=…&value=…}
     *
     * @param key   the key to store.
     * @param value the value to associate with the key.
     * @return "Success" on success, or an error message with appropriate HTTP status.
     */
    @PostMapping("/store")
    public ResponseEntity<String> put(@RequestParam String key,
                                      @RequestParam String value) {
        return routingService.put(key, value);
    }

    // ---------------------------------------------------------------
    // GET  →  GET /api/get?key=…
    // ---------------------------------------------------------------

    /**
     * Retrieves the value for the given key from the distributed store.
     *
     * The request is forwarded as: {@code GET <primaryNodeUrl>/get?key=…}
     *
     * @param key the key to look up.
     * @return the stored value, or a 404 / error body on failure.
     */
    @GetMapping("/get")
    public ResponseEntity<String> get(@RequestParam String key) {
        return routingService.get(key);
    }

    // ---------------------------------------------------------------
    // DELETE  →  DELETE /api/delete?key=…
    // ---------------------------------------------------------------

    /**
     * Deletes a key from the distributed store.
     *
     * Because the existing primary-node has no DELETE endpoint, the gateway
     * writes a tombstone marker and intercepts it on subsequent GETs.
     * See {@link GatewayRoutingService#delete(String)} for details.
     *
     * @param key the key to delete.
     * @return a confirmation message, or an error body on failure.
     */
    @DeleteMapping("/delete")
    public ResponseEntity<String> delete(@RequestParam String key) {
        return routingService.delete(key);
    }
}
