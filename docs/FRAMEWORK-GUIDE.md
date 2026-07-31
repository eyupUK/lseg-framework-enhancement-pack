# Framework Guide

## Purpose And Boundaries

The pack extends a pre-existing Java microservices test framework. Its root Maven reactor runs the added quality-test module by itself; the parent framework remains responsible for service implementation, service Compose configuration, API contracts, Pact provider verification, WireMock stubs, and Allure reporting. This repository adds focused examples around those boundaries rather than a second application platform.

```text
users service :8080 <---- orders service :8081 <---- API and k6 tests
                                  |
                                  +---- Actuator health and Prometheus metrics

order-audit Lambda ---- S3 audit bucket
       |                     |
    Moto tests          LocalStack S3 test
```

The diagram shows responsibilities, not a deployed topology. In particular, the current orders service is not wired to invoke the Lambda.

## Quality Coverage

| Layer | Source | What It Proves | External Dependency |
|---|---|---|---|
| Resilience component | `quality-engineering-tests/.../resilience` | Retry count, circuit-open fail-fast behaviour, and half-open recovery | None |
| AWS integration | `quality-engineering-tests/.../aws` | S3 put/get and correlation metadata through the AWS SDK | Docker and LocalStack |
| Service observability | `quality-engineering-tests/.../observability` | Orders health reports `UP` and Prometheus exposes a JVM metric | Running orders service |
| Lambda unit | `lambda/order-audit/tests` | Handler validation, S3 object content, and S3 metadata | Moto in-process AWS emulation |
| Service API integration | Parent `services-tests` modules | Existing users/orders API contracts | Running parent services |
| Performance smoke | `performance/k6/orders-load.js` | Order-create latency, HTTP errors, and business-response errors under a small ramp | Running orders service |

All JVM tests run by default. `ObservabilitySmokeTest` is skipped unless `orders.baseUrl` is set. `LocalStackS3IntegrationTest` is skipped when Docker is unavailable through `@Testcontainers(disabledWithoutDocker = true)`.

## Quality And Security Gates

The pack treats its automated checks as merge gates. Configure branch protection on `main` to require the checks below; the parent-service checks remain conditionally applicable because this repository does not contain the parent services.

| Gate | Workflow | Failure Condition |
|---|---|---|
| JVM tests | `LSEG Quality Gates / java-fast-tests` | Any Maven test failure |
| Lambda quality | `LSEG Quality Gates / python-lambda-tests` | Ruff reports a lint or import error |
| Lambda security | `LSEG Quality Gates / python-lambda-tests` | Bandit finds a source-code security issue or `pip-audit` finds a vulnerable pinned dependency |
| Lambda tests | `LSEG Quality Gates / python-lambda-tests` | Any Moto handler test failure |
| Terraform validation | `LSEG Quality Gates / terraform-static-validation` | Terraform is unformatted or invalid |
| Static code analysis | `Qodana / qodana` | Qodana reports any inspection problem |
| Dependency review | `Security Gates / dependency-review` | A pull request adds a dependency with a high or critical known vulnerability |
| IaC and secret scan | `Security Gates / repository-security-scan` | Trivy finds a high or critical secret or infrastructure misconfiguration |

The security scan examines Terraform, SAM, and Kubernetes manifests. It is deliberately configured to fail on high and critical findings, so new exceptions require a documented and reviewed risk decision rather than an inline suppression. Dependency Review runs only on pull requests because it compares the change against the pull-request base branch.

### Resilience Behaviour

`CircuitBreakerBehaviourTest` uses a count-based window of four calls and opens at a 50 percent failure rate. Its controlled downstream first fails four times, then verifies that the open circuit does not call the downstream, and finally proves two successful half-open calls close it.

`RetryBehaviourTest` retries `IllegalStateException` up to three attempts. The example deliberately commits its business effect only after the successful attempt. Real service calls should use the same principle with an idempotency key or transactional outbox; a retry wrapper alone cannot prevent duplicate side effects.

### Serverless Audit Contract

`lambda_handler` accepts either a JSON API-style `event.body` string or a dictionary event. It requires `orderId` and `status`, accepts optional `eventId`, and reads an optional `x-correlation-id` header. It writes this record to:

```text
s3://$AUDIT_BUCKET/orders/<orderId>/<eventId>.json
```

The response is `202` with the event ID and S3 key. Invalid JSON or missing business fields returns `400`; a missing `AUDIT_BUCKET` returns `500` without attempting an S3 call. S3 object metadata includes `correlation-id`, and structured logs include the written key and correlation ID.

The handler does not deduplicate at-least-once delivery. A caller that resends the same supplied `eventId` targets the same S3 key; a caller that omits it creates a fresh UUID and therefore a fresh record. S3 versioning preserves overwrites in the provisioned bucket but does not make event processing exactly-once. Establish event identity, replay policy, retention, encryption, and downstream reconciliation before production use.

## Deployment Assets

### SAM

`lambda/order-audit/template.yaml` defines an S3 bucket, the Python 3.12 Lambda, an API Gateway POST endpoint at `/order-audits`, least-privilege S3 write access, and a 30-day CloudWatch log group. It is self-contained from an infrastructure perspective, but the source folder has only `requirements-dev.txt`. AWS Lambda includes `boto3` in its runtime, so the handler can run; add and package a pinned production `requirements.txt` when SDK reproducibility matters.

### Terraform

`infrastructure/terraform` is an alternative infrastructure example. It creates a versioned, public-access-blocked audit bucket encrypted with a customer-managed KMS key, plus an IAM role whose writes are restricted to `orders/*` and which can generate data keys for that key. It deliberately does not create a Lambda function, attach the role to one, configure CloudWatch Logs access, or set up remote state. Do not combine the Terraform role with the SAM function without making role ownership and logging permissions explicit.

### Kubernetes

The Kustomize base deploys two replicas each of users and orders, cluster-internal Services, CPU/memory requests and limits, and Actuator readiness/liveness probes. Workloads run as UID/GID `10001` with a default seccomp profile, no Linux capabilities, no privilege escalation, and a read-only root filesystem; each receives an `emptyDir` mount at `/tmp`. It references `sample/users-service:local` and `sample/orders-service:local`; substitute images published to a registry accessible to the target cluster that can run as this non-root user. The parent applications must expose `/actuator/health/readiness` and `/actuator/health/liveness` before these manifests can become ready.

### Observability

`observability/docker-compose.observability.yml` provides Prometheus and Jaeger. Prometheus scrapes the parent services every five seconds at `/actuator/prometheus`; Jaeger exposes its UI on `16686` and OTLP on `4317` and `4318`. The services need Micrometer Prometheus support and OTLP tracing configuration respectively. The Compose file observes published host ports through `host.docker.internal`, which Docker Desktop resolves by default; Linux Docker users need an equivalent host gateway configuration.

## Integration Contracts

Apply the patch only after confirming these parent-framework contracts:

| Contract | Reason |
|---|---|
| Root Maven property `junit.jupiter.version` and dependency management for Rest Assured exist | The added module inherits the JUnit and Rest Assured versions. |
| Parent modules match the names in the workflow and README | Maven module selection otherwise fails. |
| Docker Compose publishes users `8080` and orders `8081` | API, k6, and observability defaults use those ports. |
| Orders supports `POST /orders/new` with a successful body containing `order.id` | The k6 check uses this current example contract. |
| Orders Actuator exposes health, liveness, readiness, and Prometheus | CI smoke test, Kubernetes probes, and Prometheus configuration rely on them. |
| GitHub's default branch is `main` | The patch and quality-gates workflow target `main`. |

The patch modifies an existing order CRUD test from an incorrect GET assertion to a DELETE followed by GET/404 check. Resolve any local changes in that test before applying the patch.

## Production Hardening Checklist

- Use a remote, encrypted Terraform state backend with locking before any shared-environment apply.
- Confirm lifecycle/retention, data classification, and access logging decisions for audit objects. The supplied deployment paths use customer-managed KMS encryption.
- Give the Lambda an explicit execution role with CloudWatch Logs permissions, and avoid two tools managing the same bucket or role.
- Pin and scan production Python dependencies instead of relying on a runtime-provided SDK.
- Define idempotency and replay semantics at the event boundary; retain the correlation ID across service, Lambda, and audit record logs.
- Publish immutable service images, configure a namespace and network policy, and test probes against the actual Spring Boot configuration.
- Protect `main`, make the required CI checks mandatory, and run the Pact gate only with a real release version and target environment.
