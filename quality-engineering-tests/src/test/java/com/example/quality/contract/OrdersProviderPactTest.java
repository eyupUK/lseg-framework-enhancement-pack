package com.example.quality.contract;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import com.example.orders.OrdersApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.URI;

@Provider("orders-service")
@PactFolder("src/test/resources/pacts")
class OrdersProviderPactTest {

    private static OrdersApplication orders;

    @BeforeAll
    static void startProvider() throws Exception {
        orders = new OrdersApplication((sku, quantity, idempotencyKey) -> true);
        orders.start(0);
    }

    @AfterAll
    static void stopProvider() {
        orders.close();
    }

    @BeforeEach
    void configureTarget(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", URI.create(orders.baseUrl()).getPort()));
    }

    @State("order order-1001 exists")
    void orderExists() {
        // The runnable service exposes this immutable provider-state fixture.
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyContract(PactVerificationContext context) {
        context.verifyInteraction();
    }
}
