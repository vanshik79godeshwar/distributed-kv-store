package com.kvstore.primary_node;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class PrimaryNodeController {

    private final ConcurrentHashMap<String, String> dataStore = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/get")
    public ResponseEntity<String> get(@RequestParam String key) {
        String value = dataStore.get(key);
        if (value == null) {
            return ResponseEntity.badRequest().body("Error: Key does not exist.");
        }
        return ResponseEntity.ok(value);
    }

    @PostMapping("/store")
    public ResponseEntity<String> store(@RequestParam String key, @RequestParam String value) {
        dataStore.put(key, value);

        // Fire and forget asynchronous sync
        CompletableFuture.runAsync(() -> {
            try {
                String syncUrl = String.format("http://localhost:8082/internal/sync?key=%s&value=%s", key, value);
                restTemplate.postForEntity(syncUrl, null, String.class);
            } catch (Exception e) {
                // Ignore background connection refused errors, since replica is not built yet
                System.out.println("Could not sync to replica node: " + e.getMessage());
            }
        });

        return ResponseEntity.ok("Success");
    }
}
