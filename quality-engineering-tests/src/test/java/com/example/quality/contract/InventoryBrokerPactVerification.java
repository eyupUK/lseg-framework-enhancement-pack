package com.example.quality.contract;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import au.com.dius.pact.provider.junitsupport.loader.PactBrokerAuth;
import com.example.inventory.InventoryApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.URI;

@Provider("inventory-service")
@PactBroker(
        url = "${pactbroker.url}",
        authentication = @PactBrokerAuth(token = "${pactbroker.auth.token}")
)
class InventoryBrokerPactVerification {

    private static InventoryApplication inventory;

    @BeforeAll
    static void startProvider() throws Exception {
        inventory = new InventoryApplication();
        inventory.start(0);
    }

    @AfterAll
    static void stopProvider() {
        inventory.close();
    }

    @BeforeEach
    void configureTarget(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", URI.create(inventory.baseUrl()).getPort()));
    }

    @State("inventory reservation is evaluated")
    void inventoryReservationIsEvaluated() {
        inventory.resetForProviderVerification();
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyContract(PactVerificationContext context) {
        context.verifyInteraction();
    }
}
