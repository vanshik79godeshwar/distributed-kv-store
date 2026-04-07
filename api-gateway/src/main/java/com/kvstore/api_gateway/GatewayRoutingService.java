package com.kvstore.api_gateway;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * GatewayRoutingService — proxy layer between the controller and the primary nodes.
 *
 * Responsibilities:
 *   1. Ask the {@link ConsistentHashRingService} which primary node owns the key.
 *   2. Build the correct URL for that node (translating the gateway's public
 *      endpoint names into the primary node's internal endpoint names).
 *   3. Forward the request using {@link RestTemplate}.
 *   4. Return the raw response (status + body) back to the caller.
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  Endpoint translation table                                     │
 * │  Gateway (public)       →  Primary Node (internal)             │
 * │  POST  /api/put         →  POST  /store?key=…&value=…          │
 * │  GET   /api/get         →  GET   /get?key=…                    │
 * │  DELETE /api/delete     →  POST  /store with tombstone marker  │
 * │                            (primary has no DELETE yet, so we   │
 * │                             use a special sentinel value that  │
 * │                             the gateway intercepts on GET)      │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * NOTE on DELETE: The existing primary-node has no DELETE endpoint.
 * The gateway stores a "__DELETED__" sentinel via /store and then
 * intercepts that value on /get to return a 404-equivalent.
 * This makes the feature work without touching the primary node code.
 */
@Service
public class GatewayRoutingService {

    /** Sentinel written to the primary node to mark a key as deleted. */
    public static final String DELETED_SENTINEL = "__DELETED__";

    private final ConsistentHashRingService hashRing;
    private final RestTemplate restTemplate;

    public GatewayRoutingService(ConsistentHashRingService hashRing,
                                 RestTemplate restTemplate) {
        this.hashRing = hashRing;
        this.restTemplate = restTemplate;
    }

    // ----------------------------------------------------------------
    // PUT — forward to POST /store on the responsible primary node
    // ----------------------------------------------------------------

    /**
     * Routes a PUT request: stores {@code value} for {@code key}.
     *
     * Maps to: {@code POST <nodeUrl>/store?key=…&value=…}
     *
     * @return the primary node's response body, or an error message.
     */
    public ResponseEntity<String> put(String key, String value) {
        String nodeUrl = hashRing.getNodeForKey(key);
        String targetUrl = nodeUrl + "/store?key=" + encode(key) + "&value=" + encode(value);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(targetUrl, null, String.class);
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503)
                    .body("Primary node at " + nodeUrl + " is unreachable: " + e.getMessage());
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    // ----------------------------------------------------------------
    // GET — forward to GET /get on the responsible primary node
    // ----------------------------------------------------------------

    /**
     * Routes a GET request: retrieves the value for {@code key}.
     *
     * Maps to: {@code GET <nodeUrl>/get?key=…}
     *
     * If the stored value is the delete sentinel {@link #DELETED_SENTINEL},
     * the gateway returns a 404 without exposing the implementation detail.
     *
     * @return the value or a 404 / error message.
     */
    public ResponseEntity<String> get(String key) {
        String nodeUrl = hashRing.getNodeForKey(key);
        String targetUrl = nodeUrl + "/get?key=" + encode(key);

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(targetUrl, String.class);

            // Hide the tombstone from the client.
            if (DELETED_SENTINEL.equals(response.getBody())) {
                return ResponseEntity.status(404).body("Error: Key '" + key + "' has been deleted.");
            }

            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (HttpClientErrorException e) {
            // Primary returns 400 for a missing key — convert to a clean message.
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503)
                    .body("Primary node at " + nodeUrl + " is unreachable: " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------
    // DELETE — write a tombstone via POST /store
    // ----------------------------------------------------------------

    /**
     * Routes a DELETE request by writing a tombstone marker to the primary node.
     *
     * Because the existing primary-node has no DELETE endpoint, the gateway
     * stores the sentinel value {@value #DELETED_SENTINEL} via the existing
     * {@code POST /store} endpoint.  Subsequent GET calls intercept this
     * value and convert it to a 404 response.
     *
     * Maps to: {@code POST <nodeUrl>/store?key=…&value=__DELETED__}
     *
     * @return success message or an error.
     */
    public ResponseEntity<String> delete(String key) {
        String nodeUrl = hashRing.getNodeForKey(key);
        String targetUrl = nodeUrl + "/store?key=" + encode(key) + "&value=" + DELETED_SENTINEL;

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(targetUrl, null, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return ResponseEntity.ok("Key '" + key + "' deleted successfully.");
            }
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503)
                    .body("Primary node at " + nodeUrl + " is unreachable: " + e.getMessage());
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    /**
     * Minimal URL-encoding for key/value query parameters.
     * Handles whitespace and the most common special characters.
     */
    private String encode(String value) {
        return value.replace(" ", "%20")
                    .replace("&", "%26")
                    .replace("=", "%3D")
                    .replace("+", "%2B");
    }
}
