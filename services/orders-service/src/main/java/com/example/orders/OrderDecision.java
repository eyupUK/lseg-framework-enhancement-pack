package com.example.orders;

import java.math.BigDecimal;

public record OrderDecision(String orderId, Status status, BigDecimal total, String reason) {
    public enum Status {
        ACCEPTED,
        REJECTED
    }
}
