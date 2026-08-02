package com.example.quality.contract;
import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTest;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.example.quality.order.HttpInventoryGateway;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PactConsumerTest
@PactTestFor(providerName = "inventory-service")
public class TrainingPactTest {
    @Pact(provider = "inventory-service", consumer = "orders-service")
    public V4Pact inventoryIsAvailable(PactDslWithProvider builder){
        return builder
                .given("inventory reservation is evaluated")
                .uponReceiving("a request to reserve SKU-1")
                .path("/inventory/reservations")
                .method("POST")
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody().stringType("sku", "SKU-1")
                        .integerType("quantity", 2)
                        .stringType("idempotencyKey", "corr-inventory-available")
                        .stringType("timestamp", Instant.now().toString())
                )
                .willRespondWith()
                .status(201)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody().booleanType("reserved", true))
                .toPact(V4Pact.class);
    }

    @Pact(provider = "inventory-service", consumer = "orders-service")
    public V4Pact inventoryIsUnavailable(PactDslWithProvider builder) {
        return builder.given("inventory reservation is evaluated")
                .uponReceiving("receive a request to reserve SKU-2")
                .path("/inventory/reservations")
                .method("POST")
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody().stringType("sku", "SKU-2")
                        .integerType("quantity", 3)
                        .stringType("idempotencyKey", "corr-inventory-unavailable"))
                .willRespondWith()
                .status(409)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody().booleanType("reserved", false))
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "inventoryIsAvailable")
    void shouldReserveAvailableInventory(MockServer mockServer) {
        HttpInventoryGateway gateway = new HttpInventoryGateway(mockServer.getUrl());

        assertTrue(gateway.reserve("SKU-1", 2, "corr-inventory-available"));
    }

    @Test
    @PactTestFor(pactMethod = "inventoryIsUnavailable")
    void shouldReturnFalseWhenInventoryIsUnavailable(MockServer mockServer) {
        HttpInventoryGateway gateway = new HttpInventoryGateway(mockServer.getUrl());

        assertFalse(gateway.reserve("SKU-2", 3, "corr-inventory-unavailable"));
    }

    private static V4Pact reservationPact(
            PactDslWithProvider builder,
            String sku,
            int quantity,
            String idempotencyKey,
            int status,
            boolean reserved
    ) {
        return builder
                .given("inventory reservation is evaluated")
                .uponReceiving("a request to reserve " + sku)
                .path("/inventory/reservations")
                .method("POST")
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .stringValue("sku", sku)
                        .integerType("quantity", quantity)
                        .stringValue("idempotencyKey", idempotencyKey))
                .willRespondWith()
                .status(status)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody().booleanValue("reserved", reserved))
                .toPact(V4Pact.class);
    }
}
