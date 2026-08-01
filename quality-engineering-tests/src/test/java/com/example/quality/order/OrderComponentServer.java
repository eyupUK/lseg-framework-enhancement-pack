package com.example.quality.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

final class OrderComponentServer implements AutoCloseable {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OrderService orderService;
    private HttpServer server;

    OrderComponentServer(OrderService orderService) {
        this.orderService = orderService;
    }

    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/orders", this::handleOrders);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private void handleOrders(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            write(exchange, 405, Map.of("message", "Method not allowed"), null);
            return;
        }

        try {
            OrderRequest request = objectMapper.readValue(
                    exchange.getRequestBody(),
                    OrderRequest.class);

            OrderDecision decision = orderService.placeOrder(request);
            int status = decision.status() == OrderDecision.Status.ACCEPTED ? 201 : 409;
            write(exchange, status, decision, request.correlationId());
        } catch (IdempotencyConflictException conflict) {
            write(exchange, 409, Map.of("message", conflict.getMessage()), null);
        } catch (IllegalArgumentException invalidRequest) {
            write(exchange, 400, Map.of("message", invalidRequest.getMessage()), null);
        } catch (Exception unexpected) {
            write(exchange, 500, Map.of("message", "Internal server error"), null);
        }
    }

    private void write(
            HttpExchange exchange,
            int status,
            Object response,
            String correlationId
    ) throws IOException {
        byte[] body = objectMapper.writeValueAsString(response)
                .getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        if (correlationId != null) {
            exchange.getResponseHeaders().set("X-Correlation-Id", correlationId);
        }

        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
        }
    }
}
