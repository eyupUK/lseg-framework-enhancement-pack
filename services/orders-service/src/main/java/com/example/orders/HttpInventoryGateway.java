package com.example.orders;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

public final class HttpInventoryGateway implements InventoryGateway {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI reservationsUri;

    public HttpInventoryGateway(String inventoryBaseUrl) {
        this(HttpClient.newHttpClient(), new ObjectMapper(), URI.create(inventoryBaseUrl));
    }

    HttpInventoryGateway(HttpClient httpClient, ObjectMapper objectMapper, URI inventoryBaseUri) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.reservationsUri = Objects.requireNonNull(inventoryBaseUri, "inventoryBaseUri")
                .resolve("/inventory/reservations");
    }

    @Override
    public boolean reserve(String sku, int quantity, String idempotencyKey) {
        try {
            String body = objectMapper.writeValueAsString(new ReservationRequest(sku, quantity, idempotencyKey));
            HttpRequest request = HttpRequest.newBuilder(reservationsUri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ReservationResponse reservation = objectMapper.readValue(response.body(), ReservationResponse.class);
            if (response.statusCode() == 201 && reservation.reserved()) {
                return true;
            }
            if (response.statusCode() == 409 && !reservation.reserved()) {
                return false;
            }
            throw new IllegalStateException("Inventory service returned an unexpected reservation response");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not reserve inventory", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Inventory reservation was interrupted", exception);
        }
    }

    private record ReservationRequest(String sku, int quantity, String idempotencyKey) {
    }

    private record ReservationResponse(boolean reserved) {
    }
}
