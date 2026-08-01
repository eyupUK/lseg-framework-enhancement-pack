package com.example.quality.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTest;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@PactConsumerTest
@PactTestFor(providerName = "orders-service")
class OrdersConsumerPactTest {

    @Pact(provider = "orders-service", consumer = "order-dashboard")
    public V4Pact orderExists(PactDslWithProvider builder) {
        PactDslJsonBody body = new PactDslJsonBody()
                .stringType("orderId", "order-1001")
                .stringValue("status", "CREATED")
                .decimalType("total", 19.98);

        return builder
                .given("order order-1001 exists")
                .uponReceiving("a request for an existing order")
                    .path("/orders/order-1001")
                    .method("GET")
                .willRespondWith()
                    .status(200)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(body)
                .toPact(V4Pact.class);
    }

    @Test
    void shouldReadOnlyTheFieldsRequiredByTheConsumer(MockServer mockServer) {
        given()
                .baseUri(mockServer.getUrl())
        .when()
                .get("/orders/order-1001")
        .then()
                .statusCode(200)
                .body("orderId", equalTo("order-1001"))
                .body("status", equalTo("CREATED"))
                .body("total", equalTo(19.98f));
    }
}
