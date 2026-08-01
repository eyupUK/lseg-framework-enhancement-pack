package com.example.quality.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class InventoryComponentServer implements AutoCloseable {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentMap<String, AtomicInteger> stock = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Reservation> reservations = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private HttpServer server;

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/inventory/reservations", this::handleReservation);
        server.setExecutor(executor);
        server.start();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public void setAvailableQuantity(String sku, int quantity) {
        if (sku == null || sku.isBlank() || quantity < 0) {
            throw new IllegalArgumentException("A SKU and non-negative quantity are required");
        }
        stock.put(sku, new AtomicInteger(quantity));
    }

    public int availableQuantity(String sku) {
        AtomicInteger available = stock.get(sku);
        return available == null ? 0 : available.get();
    }

    public int reservationCount() {
        return reservations.size();
    }

    private void handleReservation(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            write(exchange, 405, Map.of("message", "Method not allowed"));
            return;
        }

        try {
            ReservationRequest request = objectMapper.readValue(
                    exchange.getRequestBody(),
                    ReservationRequest.class);
            validate(request);
            Reservation reservation = reservations.compute(
                    request.idempotencyKey(),
                    (key, existing) -> existing == null
                            ? new Reservation(
                                    request.sku(),
                                    request.quantity(),
                                    reserveFromStock(request.sku(), request.quantity()))
                            : existing);
            if (!reservation.matches(request)) {
                write(exchange, 409, Map.of("message", "Idempotency key payload conflict"));
                return;
            }
            write(exchange, reservation.reserved() ? 201 : 409,
                    Map.of("reserved", reservation.reserved()));
        } catch (IllegalArgumentException invalidRequest) {
            write(exchange, 400, Map.of("message", invalidRequest.getMessage()));
        }
    }

    private boolean reserveFromStock(String sku, int quantity) {
        AtomicInteger available = stock.computeIfAbsent(sku, key -> new AtomicInteger());
        while (true) {
            int current = available.get();
            if (current < quantity) {
                return false;
            }
            if (available.compareAndSet(current, current - quantity)) {
                return true;
            }
        }
    }

    private static void validate(ReservationRequest request) {
        if (request == null || request.sku() == null || request.sku().isBlank()
                || request.idempotencyKey() == null || request.idempotencyKey().isBlank()
                || request.quantity() < 1) {
            throw new IllegalArgumentException("sku, quantity, and idempotencyKey are required");
        }
    }

    private void write(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] response = objectMapper.writeValueAsString(body)
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
        }
        executor.shutdownNow();
    }

    private record ReservationRequest(String sku, int quantity, String idempotencyKey) {
    }

    private record Reservation(String sku, int quantity, boolean reserved) {
        boolean matches(ReservationRequest request) {
            return sku.equals(request.sku()) && quantity == request.quantity();
        }
    }
}
