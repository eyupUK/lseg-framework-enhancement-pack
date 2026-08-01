package com.example.quality.order;

import java.math.BigDecimal;

public record OrderDecision(
        String orderId,
        Status status,
        BigDecimal total,
        String reason
) {
    public enum Status {
        ACCEPTED,
        REJECTED
    }
}
