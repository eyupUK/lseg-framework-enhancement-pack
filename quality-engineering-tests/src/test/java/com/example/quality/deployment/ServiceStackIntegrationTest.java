package com.example.quality.deployment;

import com.example.inventory.InventoryApplication;
import com.example.orders.OrdersApplication;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

class ServiceStackIntegrationTest {

    private InventoryApplication inventory;
    private OrdersApplication orders;

    @BeforeEach
    void startServices() throws Exception {
        inventory = new InventoryApplication();
        inventory.start(0);
        inventory.setAvailableQuantity("SKU-1", 2);
        orders = new OrdersApplication(inventory.baseUrl());
        orders.start(0);
    }

    @AfterEach
    void stopServices() {
        orders.close();
        inventory.close();
    }

    @Test
    void shouldCreateAnOrderThroughTheRunnableServiceStack() {
        given()
                .baseUri(orders.baseUrl())
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "orderId", "order-service-stack-1",
                        "sku", "SKU-1",
                        "quantity", 2,
                        "unitPrice", 9.99,
                        "correlationId", "service-stack-1"))
        .when()
                .post("/orders")
        .then()
                .statusCode(201)
                .body("status", equalTo("ACCEPTED"))
                .body("total", equalTo(19.98f));
    }

    @Test
    void shouldExposeOrdersHealthAndPrometheusMetrics() {
        given().baseUri(orders.baseUrl()).when().get("/actuator/health")
                .then().statusCode(200).body("status", equalTo("UP"));
        given().baseUri(orders.baseUrl()).accept("text/plain").when().get("/actuator/prometheus")
                .then().statusCode(200).body(containsString("jvm_memory_used_bytes"));
    }
}
