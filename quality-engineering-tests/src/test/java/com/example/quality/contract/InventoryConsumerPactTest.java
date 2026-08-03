package com.example.quality.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTest;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.example.orders.HttpInventoryGateway;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PactConsumerTest
@PactTestFor(providerName = "inventory-service")
class InventoryConsumerPactTest {

    @Pact(provider = "inventory-service", consumer = "orders-service")
    public V4Pact inventoryIsAvailable(PactDslWithProvider builder) {
        return reservationPact(builder, "SKU-1", 2, "corr-inventory-available", 201, true);
    }

    @Pact(provider = "inventory-service", consumer = "orders-service")
    public V4Pact inventoryIsUnavailable(PactDslWithProvider builder) {
        return reservationPact(builder, "SKU-2", 3, "corr-inventory-unavailable", 409, false);
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
