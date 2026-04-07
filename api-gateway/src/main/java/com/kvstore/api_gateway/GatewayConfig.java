package com.kvstore.api_gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

/**
 * Central Spring configuration for the API Gateway.
 *
 * Reads the node list and virtual-node count from application.properties
 * and exposes them as beans consumed by ConsistentHashRingService.
 */
@Configuration
public class GatewayConfig {

    /**
     * Comma-separated list of primary-node base URLs, e.g.:
     *   http://localhost:8081,http://localhost:8083
     *
     * Injected from: gateway.primary-nodes in application.properties
     */
    @Value("${gateway.primary-nodes}")
    private String primaryNodesCsv;

    /** Number of virtual nodes per real node on the hash ring. */
    @Value("${gateway.virtual-nodes:150}")
    private int virtualNodes;

    /** Parsed list of primary-node base URLs. */
    @Bean
    public List<String> primaryNodeUrls() {
        return Arrays.stream(primaryNodesCsv.split(","))
                .map(String::trim)
                .toList();
    }

    @Bean
    public int virtualNodeCount() {
        return virtualNodes;
    }

    /**
     * Shared RestTemplate used to proxy requests to the primary nodes.
     * Declared as a bean so it can be injected and replaced in tests.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
