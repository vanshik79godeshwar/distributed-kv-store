package com.kvstore.replica_node;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ConcurrentHashMap;

@RestController
public class ReplicaNodeController {

    private final ConcurrentHashMap<String, String> dataStore = new ConcurrentHashMap<>();
    private boolean isPromoted = false;

    @PostMapping("/internal/sync")
    public ResponseEntity<String> sync(@RequestParam String key, @RequestParam String value) {
        dataStore.put(key, value);
        return ResponseEntity.ok("Synced successfully");
    }

    @PostMapping("/store")
    public ResponseEntity<String> store(@RequestParam String key, @RequestParam String value) {
        if (!isPromoted) {
            System.out.println("🚨 FAILOVER EVENT DETECTED 🚨");
            System.out.println("Replica Node received direct traffic. Promoting to Primary!");
            isPromoted = true;
        }
        
        dataStore.put(key, value);
        
        return ResponseEntity.ok("Success - Handled by Promoted Replica");
    }

    @GetMapping("/get")
    public ResponseEntity<String> get(@RequestParam String key) {
        String value = dataStore.get(key);
        if (value == null) {
            return ResponseEntity.badRequest().body("Error: Key does not exist.");
        }
        return ResponseEntity.ok(value);
    }
}
