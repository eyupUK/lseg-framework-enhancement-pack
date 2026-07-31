package com.example.quality.aws;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

@Testcontainers(disabledWithoutDocker = true)
@Tag("aws-integration")
class LocalStackS3IntegrationTest {

    private static final String BUCKET = "order-audit-test";

    @Container
    static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.8.1"))
            .withServices(S3);

    private static S3Client s3;

    @BeforeAll
    static void createClientAndBucket() {
        s3 = S3Client.builder()
                .endpointOverride(LOCALSTACK.getEndpointOverride(S3))
                .region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .forcePathStyle(true)
                .build();

        s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    }

    @AfterAll
    static void closeClient() {
        if (s3 != null) {
            s3.close();
        }
    }

    @Test
    void shouldPersistAndReadAnAuditRecordWithCorrelationMetadata() {
        String key = "orders/1001.json";
        String json = "{\"orderId\":1001,\"status\":\"CREATED\"}";
        String correlationId = "corr-1001";

        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(BUCKET)
                        .key(key)
                        .contentType("application/json")
                        .metadata(Map.of("correlation-id", correlationId))
                        .build(),
                RequestBody.fromString(json, StandardCharsets.UTF_8));

        var response = s3.getObjectAsBytes(
                GetObjectRequest.builder().bucket(BUCKET).key(key).build());

        assertEquals(json, response.asUtf8String());
        assertEquals(correlationId, response.response().metadata().get("correlation-id"));
    }
}
