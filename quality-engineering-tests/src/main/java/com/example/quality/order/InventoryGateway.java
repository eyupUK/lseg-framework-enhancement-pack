package com.example.quality.order;

public interface InventoryGateway {
    boolean reserve(String sku, int quantity, String idempotencyKey);
}
