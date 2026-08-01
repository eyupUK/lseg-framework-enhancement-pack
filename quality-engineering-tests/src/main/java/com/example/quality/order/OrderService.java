package com.example.quality.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

public final class OrderService {

    private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");
    private static final BigDecimal MAX_PRICE = new BigDecimal("100000.00");

    private final InventoryGateway inventoryGateway;
    private final ConcurrentMap<String, CachedOrder> processedRequests = new ConcurrentHashMap<>();

    public OrderService(InventoryGateway inventoryGateway) {
        this.inventoryGateway = Objects.requireNonNull(inventoryGateway, "inventoryGateway");
    }

    public OrderDecision placeOrder(OrderRequest request) {
        validate(request);

        String fingerprint = fingerprint(request);
        AtomicReference<OrderDecision> result = new AtomicReference<>();

        processedRequests.compute(request.correlationId(), (key, existing) -> {
            if (existing != null) {
                if (!existing.fingerprint().equals(fingerprint)) {
                    throw new IdempotencyConflictException(
                            "Idempotency key was already used for a different order payload");
                }
                result.set(existing.decision());
                return existing;
            }

            boolean reserved = inventoryGateway.reserve(
                    request.sku(),
                    request.quantity(),
                    request.correlationId());

            BigDecimal total = request.unitPrice()
                    .multiply(BigDecimal.valueOf(request.quantity()))
                    .setScale(2, RoundingMode.HALF_UP);

            OrderDecision decision = reserved
                    ? new OrderDecision(
                            request.orderId(),
                            OrderDecision.Status.ACCEPTED,
                            total,
                            null)
                    : new OrderDecision(
                            request.orderId(),
                            OrderDecision.Status.REJECTED,
                            total,
                            "OUT_OF_STOCK");

            result.set(decision);
            return new CachedOrder(fingerprint, decision);
        });

        return result.get();
    }

    private static void validate(OrderRequest request) {
        Objects.requireNonNull(request, "request");
        requireText(request.orderId(), "orderId");
        requireText(request.sku(), "sku");
        requireText(request.correlationId(), "correlationId");

        if (request.quantity() < 1 || request.quantity() > 100) {
            throw new IllegalArgumentException("quantity must be between 1 and 100");
        }

        if (request.unitPrice() == null
                || request.unitPrice().compareTo(MIN_PRICE) < 0
                || request.unitPrice().compareTo(MAX_PRICE) > 0) {
            throw new IllegalArgumentException(
                    "unitPrice must be between 0.01 and 100000.00");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static String fingerprint(OrderRequest request) {
        return String.join("|",
                request.orderId(),
                request.sku(),
                Integer.toString(request.quantity()),
                request.unitPrice().stripTrailingZeros().toPlainString());
    }

    private record CachedOrder(String fingerprint, OrderDecision decision) {
    }
}
