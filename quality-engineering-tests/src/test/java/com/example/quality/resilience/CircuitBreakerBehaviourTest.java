package com.example.quality.resilience;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("resilience")
class CircuitBreakerBehaviourTest {

    @Test
    void shouldOpenFailFastThenRecoverThroughHalfOpen() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMillis(150))
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .permittedNumberOfCallsInHalfOpenState(2)
                .build();

        CircuitBreaker breaker = CircuitBreaker.of("users", config);
        AtomicInteger downstreamCalls = new AtomicInteger();

        Supplier<String> failingDownstream = CircuitBreaker.decorateSupplier(breaker, () -> {
            downstreamCalls.incrementAndGet();
            throw new IllegalStateException("users-service unavailable");
        });

        for (int i = 0; i < 4; i++) {
            assertThrows(IllegalStateException.class, failingDownstream::get);
        }

        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
        assertEquals(4, downstreamCalls.get());

        assertThrows(CallNotPermittedException.class, failingDownstream::get);
        assertEquals(4, downstreamCalls.get(), "OPEN circuit must fail fast without calling downstream");

        await().atMost(Duration.ofSeconds(1))
                .until(() -> breaker.getState() == CircuitBreaker.State.HALF_OPEN);

        Supplier<String> recoveredDownstream = CircuitBreaker.decorateSupplier(breaker, () -> {
            downstreamCalls.incrementAndGet();
            return "recovered";
        });

        assertEquals("recovered", recoveredDownstream.get());
        assertEquals("recovered", recoveredDownstream.get());
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
    }
}
