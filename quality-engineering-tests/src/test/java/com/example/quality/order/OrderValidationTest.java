package com.example.quality.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderValidationTest {

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService((sku, quantity, idempotencyKey) -> true);
    }

    @ParameterizedTest(name = "quantity {0} is outside the valid partition")
    @ValueSource(ints = {-1, 0, 101})
    void shouldRejectInvalidQuantityPartitions(int quantity) {
        assertThrows(
                IllegalArgumentException.class,
                () -> orderService.placeOrder(request(quantity, "10.00", "corr-" + quantity)));
    }

    @ParameterizedTest(name = "quantity boundary {0} is valid")
    @ValueSource(ints = {1, 100})
    void shouldAcceptQuantityBoundaryValues(int quantity) {
        assertDoesNotThrow(
                () -> orderService.placeOrder(request(quantity, "10.00", "corr-" + quantity)));
    }

    @ParameterizedTest(name = "price {0} is invalid")
    @CsvSource({"0.00", "-0.01", "100000.01"})
    void shouldRejectInvalidPricePartitions(String price) {
        assertThrows(
                IllegalArgumentException.class,
                () -> orderService.placeOrder(request(1, price, "corr-" + price)));
    }

    @Test
    void shouldAcceptPriceBoundaryValues() {
        assertDoesNotThrow(
                () -> orderService.placeOrder(request(1, "0.01", "corr-min-price")));
        assertDoesNotThrow(
                () -> orderService.placeOrder(request(1, "100000.00", "corr-max-price")));
    }

    private static OrderRequest request(int quantity, String price, String correlationId) {
        return new OrderRequest(
                "order-" + correlationId,
                "SKU-1",
                quantity,
                new BigDecimal(price),
                correlationId);
    }
}
