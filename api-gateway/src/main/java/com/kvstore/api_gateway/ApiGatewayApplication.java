package com.kvstore.api_gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the API Gateway microservice.
 *
 * Role in the system:
 *   Client-Simulator  ──►  API Gateway (8080)  ──►  Primary Node(s) (8081, …)
 *                                                         │
 *                                                         └──►  Replica Node(s) (8082, …)
 *
 * The gateway intercepts /api/put, /api/get, /api/delete requests coming
 * from the client simulator, selects the responsible primary node via a
 * consistent hash ring, and proxies the request using RestTemplate.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
