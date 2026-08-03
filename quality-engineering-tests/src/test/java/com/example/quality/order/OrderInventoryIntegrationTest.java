package com.example.quality.order;

import com.example.orders.HttpInventoryGateway;
import com.example.orders.OrderService;
import com.example.quality.inventory.InventoryComponentServer;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderInventoryIntegrationTest {

    private InventoryComponentServer inventoryServer;
    private OrderComponentServer orderServer;

    @BeforeEach
    void startServices() throws Exception {
        inventoryServer = new InventoryComponentServer();
        inventoryServer.start();
        orderServer = new OrderComponentServer(
                new OrderService(new HttpInventoryGateway(inventoryServer.baseUrl())));
        orderServer.start();
    }

    @AfterEach
    void stopServices() {
        orderServer.close();
        inventoryServer.close();
    }

    @Test
    void shouldReserveInventoryThroughTheOrderHttpBoundary() {
        inventoryServer.setAvailableQuantity("SKU-1", 2);

        createOrder("order-integration-1", "SKU-1", 2, "corr-integration-1")
                .statusCode(201)
                .body("status", equalTo("ACCEPTED"));

        assertEquals(0, inventoryServer.availableQuantity("SKU-1"));
        assertEquals(1, inventoryServer.reservationCount());
    }

    @Test
    void shouldRejectAnOrderWhenTheInventoryServiceIsOutOfStock() {
        inventoryServer.setAvailableQuantity("SKU-1", 1);

        createOrder("order-integration-2", "SKU-1", 2, "corr-integration-2")
                .statusCode(409)
                .body("status", equalTo("REJECTED"))
                .body("reason", equalTo("OUT_OF_STOCK"));

        assertEquals(1, inventoryServer.availableQuantity("SKU-1"));
        assertEquals(1, inventoryServer.reservationCount());
    }

    @Test
    void shouldNotReserveInventoryTwiceForAnIdempotentOrderRetry() {
        inventoryServer.setAvailableQuantity("SKU-1", 3);

        createOrder("order-integration-3", "SKU-1", 1, "corr-integration-3")
                .statusCode(201);
        createOrder("order-integration-3", "SKU-1", 1, "corr-integration-3")
                .statusCode(201);

        assertEquals(2, inventoryServer.availableQuantity("SKU-1"));
        assertEquals(1, inventoryServer.reservationCount());
    }

    private ValidatableResponse createOrder(
            String orderId,
            String sku,
            int quantity,
            String correlationId
    ) {
        return given()
                .baseUri(orderServer.baseUrl())
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "orderId", orderId,
                        "sku", sku,
                        "quantity", quantity,
                        "unitPrice", 9.99,
                        "correlationId", correlationId))
                .when()
                .post("/orders")
                .then();
    }
}
