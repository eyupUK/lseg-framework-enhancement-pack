package com.example.users;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UsersApplication implements AutoCloseable {

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private HttpServer server;

    public static void main(String[] args) throws Exception {
        UsersApplication application = new UsersApplication();
        application.start(Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080")));
        Runtime.getRuntime().addShutdownHook(new Thread(application::close));
    }

    public void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/actuator/health", this::health);
        server.createContext("/actuator/health/readiness", this::health);
        server.createContext("/actuator/health/liveness", this::health);
        server.createContext("/actuator/prometheus", this::prometheus);
        server.setExecutor(executor);
        server.start();
    }

    private void health(HttpExchange exchange) throws IOException {
        byte[] body = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void prometheus(HttpExchange exchange) throws IOException {
        byte[] body = "jvm_memory_used_bytes 0\n".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
        }
        executor.shutdownNow();
    }
}
