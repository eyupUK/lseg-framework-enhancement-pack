package com.example.inventory;

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

public final class InventoryApplication implements AutoCloseable {

    private static final int DEFAULT_STOCK = 1_000;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentMap<String, AtomicInteger> stock = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Reservation> reservations = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private HttpServer server;

    public static void main(String[] args) throws Exception {
        InventoryApplication application = new InventoryApplication();
        application.start(portFromEnvironment());
        Runtime.getRuntime().addShutdownHook(new Thread(application::close));
    }

    public void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/inventory/reservations", this::handleReservation);
        server.createContext("/actuator/health", exchange -> health(exchange, "health"));
        server.createContext("/actuator/health/readiness", exchange -> health(exchange, "readiness"));
        server.createContext("/actuator/health/liveness", exchange -> health(exchange, "liveness"));
        server.createContext("/actuator/prometheus", this::prometheus);
        server.setExecutor(executor);
        server.start();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public void resetForProviderVerification() {
        stock.clear();
        reservations.clear();
        setAvailableQuantity("SKU-1", 2);
        setAvailableQuantity("SKU-2", 0);
    }

    public void setAvailableQuantity(String sku, int quantity) {
        if (sku == null || sku.isBlank() || quantity < 0) {
            throw new IllegalArgumentException("A SKU and non-negative quantity are required");
        }
        stock.put(sku, new AtomicInteger(quantity));
    }

    public int availableQuantity(String sku) {
        AtomicInteger available = stock.get(sku);
        return available == null ? DEFAULT_STOCK : available.get();
    }

    private void handleReservation(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, Map.of("message", "Method not allowed"));
            return;
        }

        try {
            ReservationRequest request = objectMapper.readValue(exchange.getRequestBody(), ReservationRequest.class);
            validate(request);
            Reservation reservation = reservations.compute(request.idempotencyKey(), (key, existing) -> {
                if (existing == null) {
                    return new Reservation(
                            request.sku(),
                            request.quantity(),
                            reserveFromStock(request.sku(), request.quantity()));
                }
                return existing;
            });

            if (!reservation.matches(request)) {
                writeJson(exchange, 409, Map.of("message", "Idempotency key payload conflict"));
                return;
            }
            writeJson(exchange, reservation.reserved() ? 201 : 409, Map.of("reserved", reservation.reserved()));
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 400, Map.of("message", exception.getMessage()));
        }
    }

    private boolean reserveFromStock(String sku, int quantity) {
        AtomicInteger available = stock.computeIfAbsent(sku, ignored -> new AtomicInteger(DEFAULT_STOCK));
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

    private void health(HttpExchange exchange, String ignored) throws IOException {
        writeJson(exchange, 200, Map.of("status", "UP"));
    }

    private void prometheus(HttpExchange exchange) throws IOException {
        byte[] body = "jvm_memory_used_bytes 0\n".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void writeJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] response = objectMapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private static void validate(ReservationRequest request) {
        if (request == null || request.sku() == null || request.sku().isBlank()
                || request.idempotencyKey() == null || request.idempotencyKey().isBlank()
                || request.quantity() < 1) {
            throw new IllegalArgumentException("sku, quantity, and idempotencyKey are required");
        }
    }

    private static int portFromEnvironment() {
        return Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8082"));
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
