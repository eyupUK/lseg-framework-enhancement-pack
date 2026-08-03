package com.example.quality.order;

import com.example.orders.InventoryGateway;
import com.example.orders.OrderService;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderApiComponentTest {

    @Mock
    private InventoryGateway inventoryGateway;

    private OrderComponentServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new OrderComponentServer(new OrderService(inventoryGateway));
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void shouldCreateAnOrderThroughTheHttpBoundary() {
        when(inventoryGateway.reserve("SKU-1", 2, "corr-api-1")).thenReturn(true);

        given()
                .baseUri(server.baseUrl())
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "orderId", "order-api-1",
                        "sku", "SKU-1",
                        "quantity", 2,
                        "unitPrice", 9.99,
                        "correlationId", "corr-api-1"))
        .when()
                .post("/orders")
        .then()
                .statusCode(201)
                .header("X-Correlation-Id", "corr-api-1")
                .body("orderId", equalTo("order-api-1"))
                .body("status", equalTo("ACCEPTED"))
                .body("total", equalTo(19.98f));

        verify(inventoryGateway).reserve("SKU-1", 2, "corr-api-1");
    }

    @Test
    void shouldMapInvalidInputToBadRequestWithoutCallingInventory() {
        given()
                .baseUri(server.baseUrl())
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "orderId", "order-api-2",
                        "sku", "SKU-1",
                        "quantity", 0,
                        "unitPrice", 9.99,
                        "correlationId", "corr-api-2"))
        .when()
                .post("/orders")
        .then()
                .statusCode(400)
                .body("message", equalTo("quantity must be between 1 and 100"));

        verify(inventoryGateway, never())
                .reserve("SKU-1", 0, "corr-api-2");
    }

    @Test
    void shouldNotReserveInventoryTwiceForADuplicateHttpRequest() {
        when(inventoryGateway.reserve("SKU-1", 1, "corr-api-3")).thenReturn(true);

        Map<String, Object> request = Map.of(
                "orderId", "order-api-3",
                "sku", "SKU-1",
                "quantity", 1,
                "unitPrice", 5.00,
                "correlationId", "corr-api-3");

        given().baseUri(server.baseUrl()).contentType(ContentType.JSON)
                .body(request).post("/orders").then().statusCode(201);
        given().baseUri(server.baseUrl()).contentType(ContentType.JSON)
                .body(request).post("/orders").then().statusCode(201);

        verify(inventoryGateway).reserve("SKU-1", 1, "corr-api-3");
    }
}
