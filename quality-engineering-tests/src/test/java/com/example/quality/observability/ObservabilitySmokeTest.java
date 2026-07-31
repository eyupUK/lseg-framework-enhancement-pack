package com.example.quality.observability;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("observability")
class ObservabilitySmokeTest {

    @Test
    void shouldExposeHealthAndPrometheusMetrics() {
        String baseUrl = System.getProperty("orders.baseUrl", "");
        assumeTrue(!baseUrl.isBlank(), "Run with -Dorders.baseUrl=http://localhost:8081");

        given()
                .baseUri(baseUrl)
                .when().get("/actuator/health")
                .then().statusCode(200)
                .body("status", equalTo("UP"));

        given()
                .baseUri(baseUrl)
                .accept("text/plain")
                .when().get("/actuator/prometheus")
                .then().statusCode(200)
                .body(containsString("jvm_memory_used_bytes"));
    }
}
