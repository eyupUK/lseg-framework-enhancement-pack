package com.example.quality.order;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class
HttpInventoryGatewayWireMockTest {

    @RegisterExtension
    static final WireMockExtension inventoryService = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Test
    void shouldSendTheReservationContractAndAcceptAvailableStock() {
        inventoryService.stubFor(post(urlEqualTo("/inventory/reservations"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson("""
                        {"sku":"SKU-1","quantity":2,"idempotencyKey":"corr-wiremock-available"}
                        """))
                .willReturn(reservationResponse(201, "{\"reserved\":true}")));

        boolean reserved = gateway().reserve("SKU-1", 2, "corr-wiremock-available");

        assertTrue(reserved);
        inventoryService.verify(postRequestedFor(urlEqualTo("/inventory/reservations")));
    }

    @Test
    void shouldReturnFalseWhenTheInventoryServiceRejectsTheReservation() {
        inventoryService.stubFor(post(urlEqualTo("/inventory/reservations"))
                .willReturn(reservationResponse(409, "{\"reserved\":false}")));

        boolean reserved = gateway().reserve("SKU-2", 3, "corr-wiremock-unavailable");

        assertFalse(reserved);
    }

    @Test
    void shouldRejectAnUnexpectedInventoryResponse() {
        inventoryService.stubFor(post(urlEqualTo("/inventory/reservations"))
                .willReturn(reservationResponse(503, "{\"reserved\":false}")));

        assertThrows(
                IllegalStateException.class,
                () -> gateway().reserve("SKU-3", 1, "corr-wiremock-unavailable"));
    }

    @Test
    void shouldRejectAMalformedInventoryResponse() {
        inventoryService.stubFor(post(urlEqualTo("/inventory/reservations"))
                .willReturn(reservationResponse(201, "{\"reserved\":{}}")));

        assertThrows(
                IllegalStateException.class,
                () -> gateway().reserve("SKU-4", 1, "corr-wiremock-malformed"));
    }

    private HttpInventoryGateway gateway() {
        return new HttpInventoryGateway(inventoryService.getRuntimeInfo().getHttpBaseUrl());
    }

    private static ResponseDefinitionBuilder reservationResponse(
            int status,
            String body
    ) {
        return aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }
}
