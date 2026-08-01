package com.example.quality.order;

import java.math.BigDecimal;

public record OrderRequest(
        String orderId,
        String sku,
        int quantity,
        BigDecimal unitPrice,
        String correlationId
) {
}
