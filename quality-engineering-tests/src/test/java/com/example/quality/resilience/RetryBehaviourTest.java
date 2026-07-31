package com.example.quality.resilience;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("resilience")
class RetryBehaviourTest {

    @Test
    void shouldRetryTransientFailuresButAvoidDuplicateBusinessEffects() {
        Retry retry = Retry.of("users", RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(5))
                .retryExceptions(IllegalStateException.class)
                .build());

        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger committedBusinessEffects = new AtomicInteger();

        Supplier<String> operation = Retry.decorateSupplier(retry, () -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                throw new IllegalStateException("temporary network failure");
            }
            committedBusinessEffects.incrementAndGet();
            return "accepted";
        });

        assertEquals("accepted", operation.get());
        assertEquals(3, attempts.get());
        assertEquals(1, committedBusinessEffects.get(),
                "Retries must not duplicate the final business side effect");
    }
}
