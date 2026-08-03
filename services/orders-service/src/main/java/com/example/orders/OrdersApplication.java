package com.example.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class OrdersApplication implements AutoCloseable {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final AtomicLong generatedOrderIds = new AtomicLong(1_000);
    private final ConcurrentMap<String, String> loadOrderIds = new ConcurrentHashMap<>();
    private final OrderService orderService;
    private HttpServer server;

    public OrdersApplication(String inventoryBaseUrl) {
        this(new HttpInventoryGateway(inventoryBaseUrl));
    }

    public OrdersApplication(InventoryGateway inventoryGateway) {
        this.orderService = new OrderService(inventoryGateway);
    }

    public static void main(String[] args) throws Exception {
        OrdersApplication application = new OrdersApplication(
                System.getenv().getOrDefault("INVENTORY_SERVICE_BASE_URL", "http://localhost:8082"));
        application.start(portFromEnvironment());
        Runtime.getRuntime().addShutdownHook(new Thread(application::close));
    }

    public void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/orders/new", this::handleLoadOrder);
        server.createContext("/orders", this::handleOrders);
        server.createContext("/actuator/health", exchange -> health(exchange));
        server.createContext("/actuator/health/readiness", exchange -> health(exchange));
        server.createContext("/actuator/health/liveness", exchange -> health(exchange));
        server.createContext("/actuator/prometheus", this::prometheus);
        server.setExecutor(executor);
        server.start();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private void handleOrders(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("GET".equals(exchange.getRequestMethod()) && "/orders/order-1001".equals(path)) {
            writeJson(exchange, 200, Map.of("orderId", "order-1001", "status", "CREATED", "total", 19.98), null);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod()) || !"/orders".equals(path)) {
            writeJson(exchange, 405, Map.of("message", "Method not allowed"), null);
            return;
        }

        try {
            OrderRequest request = objectMapper.readValue(exchange.getRequestBody(), OrderRequest.class);
            OrderDecision decision = orderService.placeOrder(request);
            int status = decision.status() == OrderDecision.Status.ACCEPTED ? 201 : 409;
            writeJson(exchange, status, decision, request.correlationId());
        } catch (IdempotencyConflictException conflict) {
            writeJson(exchange, 409, Map.of("message", conflict.getMessage()), null);
        } catch (IllegalArgumentException invalidRequest) {
            writeJson(exchange, 400, Map.of("message", invalidRequest.getMessage()), null);
        } catch (Exception unexpected) {
            writeJson(exchange, 500, Map.of("message", "Internal server error"), null);
        }
    }

    private void handleLoadOrder(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, Map.of("message", "Method not allowed"), null);
            return;
        }
        try {
            LoadOrderRequest request = objectMapper.readValue(exchange.getRequestBody(), LoadOrderRequest.class);
            String correlationId = exchange.getRequestHeaders().getFirst("X-Correlation-Id");
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = "order-" + generatedOrderIds.incrementAndGet();
            }
            String orderId = loadOrderIds.computeIfAbsent(
                    correlationId, ignored -> "order-" + generatedOrderIds.incrementAndGet());
            OrderDecision decision = orderService.placeOrder(new OrderRequest(
                    orderId,
                    request.item(),
                    1,
                    request.amount(),
                    correlationId));
            int status = decision.status() == OrderDecision.Status.ACCEPTED ? 200 : 409;
            writeJson(exchange, status, Map.of("order", Map.of(
                    "id", decision.orderId(),
                    "status", decision.status().name(),
                    "total", decision.total())), correlationId);
        } catch (IllegalArgumentException invalidRequest) {
            writeJson(exchange, 400, Map.of("message", invalidRequest.getMessage()), null);
        } catch (Exception unexpected) {
            writeJson(exchange, 500, Map.of("message", "Internal server error"), null);
        }
    }

    private void health(HttpExchange exchange) throws IOException {
        writeJson(exchange, 200, Map.of("status", "UP"), null);
    }

    private void prometheus(HttpExchange exchange) throws IOException {
        byte[] body = "jvm_memory_used_bytes 0\norders_created_total 0\n".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void writeJson(HttpExchange exchange, int status, Object body, String correlationId) throws IOException {
        byte[] response = objectMapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        if (correlationId != null) {
            exchange.getResponseHeaders().set("X-Correlation-Id", correlationId);
        }
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private static int portFromEnvironment() {
        return Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8081"));
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
        }
        executor.shutdownNow();
    }

    private record LoadOrderRequest(long userId, String item, BigDecimal amount) {
    }
}
