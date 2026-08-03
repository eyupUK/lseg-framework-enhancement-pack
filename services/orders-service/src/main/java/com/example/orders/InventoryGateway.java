package com.example.orders;

public interface InventoryGateway {
    boolean reserve(String sku, int quantity, String idempotencyKey);
}
