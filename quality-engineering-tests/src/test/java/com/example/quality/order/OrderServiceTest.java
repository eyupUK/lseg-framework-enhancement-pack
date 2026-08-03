package com.example.quality.order;

import com.example.orders.IdempotencyConflictException;
import com.example.orders.InventoryGateway;
import com.example.orders.OrderDecision;
import com.example.orders.OrderRequest;
import com.example.orders.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private InventoryGateway inventoryGateway;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(inventoryGateway);
    }

    @Test
    void shouldAcceptAnOrderAndRoundTheTotalToTwoDecimalPlaces() {
        OrderRequest request = request("order-1001", "corr-1001", 3, "6.666");
        when(inventoryGateway.reserve("SKU-1", 3, "corr-1001")).thenReturn(true);

        OrderDecision decision = orderService.placeOrder(request);

        assertEquals(OrderDecision.Status.ACCEPTED, decision.status());
        assertEquals(new BigDecimal("20.00"), decision.total());
        assertNull(decision.reason());
        verify(inventoryGateway).reserve("SKU-1", 3, "corr-1001");
    }

    @Test
    void shouldRejectAnOrderWhenInventoryCannotBeReserved() {
        OrderRequest request = request("order-1002", "corr-1002", 2, "10.00");
        when(inventoryGateway.reserve("SKU-1", 2, "corr-1002")).thenReturn(false);

        OrderDecision decision = orderService.placeOrder(request);

        assertEquals(OrderDecision.Status.REJECTED, decision.status());
        assertEquals("OUT_OF_STOCK", decision.reason());
        assertEquals(new BigDecimal("20.00"), decision.total());
    }

    @Test
    void shouldReturnTheOriginalDecisionForAnIdempotentDuplicate() {
        OrderRequest request = request("order-1003", "corr-1003", 1, "12.50");
        when(inventoryGateway.reserve("SKU-1", 1, "corr-1003")).thenReturn(true);

        OrderDecision first = orderService.placeOrder(request);
        OrderDecision duplicate = orderService.placeOrder(request);

        assertSame(first, duplicate);
        verify(inventoryGateway).reserve("SKU-1", 1, "corr-1003");
        verifyNoMoreInteractions(inventoryGateway);
    }

    @Test
    void shouldRejectReuseOfAnIdempotencyKeyForADifferentPayload() {
        when(inventoryGateway.reserve("SKU-1", 1, "corr-shared")).thenReturn(true);

        orderService.placeOrder(request("order-1004", "corr-shared", 1, "10.00"));

        IdempotencyConflictException error = assertThrows(
                IdempotencyConflictException.class,
                () -> orderService.placeOrder(
                        request("order-1005", "corr-shared", 2, "10.00")));

        assertEquals(
                "Idempotency key was already used for a different order payload",
                error.getMessage());
        verify(inventoryGateway).reserve("SKU-1", 1, "corr-shared");
        verifyNoMoreInteractions(inventoryGateway);
    }

    private static OrderRequest request(
            String orderId,
            String correlationId,
            int quantity,
            String unitPrice
    ) {
        return new OrderRequest(
                orderId,
                "SKU-1",
                quantity,
                new BigDecimal(unitPrice),
                correlationId);
    }
}
