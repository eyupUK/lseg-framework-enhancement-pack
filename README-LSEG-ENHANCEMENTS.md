# LSEG Quality Engineering Enhancement Pack

This pack has a standalone Maven reactor for its own quality-engineering tests and can also be overlaid onto `springboot-microservices-framework`. The parent framework remains necessary for the service API, Compose, Pact, WireMock, and Allure workflows.

The overlay assumes the parent framework provides:

- A root Maven `pom.xml` with the `junit.jupiter.version` property and dependency management for Rest Assured.
- Maven modules named `services/users-service`, `services/orders-service`, `services-tests/users-api-tests`, and `services-tests/orders-api-tests`.
- A `docker compose` configuration exposing users on port `8080` and orders on port `8081`.
- Spring Boot Actuator health and Prometheus endpoints on the orders service.

## Contents

| Area | Implementation |
|---|---|
| AWS emulation | Testcontainers and LocalStack S3 integration test |
| Serverless audit | Python order-audit Lambda, SAM template, and Moto unit tests |
| Order/inventory component | HTTP inventory reservation service, order HTTP gateway, component integration, WireMock, and Pact consumer contract tests |
| Resilience | Deterministic Resilience4j circuit-breaker and retry tests |
| Observability | Actuator/Prometheus smoke test and optional Prometheus/Jaeger stack |
| Delivery gates | GitHub Actions quality, dependency, Gitleaks secret, and IaC security gates plus a Pact Broker `can-i-deploy` script |
| Performance | k6 order-create load smoke test with latency and error thresholds |
| Infrastructure | Terraform S3 bucket and least-privilege Lambda execution role example |
| Kubernetes | Users and orders Deployments, Services, probes, resources, and Kustomize |
| Developer environment | Java/Python/Terraform Dev Container and Podman notes |

## Install Into The Parent Framework

From the root of the parent repository:

```bash
unzip lseg-framework-enhancement-pack.zip -d /tmp/lseg-pack
rsync -a --exclude 'pom.xml' /tmp/lseg-pack/lseg-framework-enhancement-pack/ ./
git apply existing-repo-fixes.patch
git diff --check
```

The patch registers `quality-engineering-tests`, corrects the order-delete assertion, and changes the existing workflow branch/path settings to match `main` and `services-tests`.

The pack's root `pom.xml` is for standalone quality-test execution; do not copy it over the parent framework's root POM. Review all overlay and patch changes before committing. The pack does not include the parent Compose file, application configuration, or service images.

## Quick Verification

Java 17, Maven, and Python 3.12 are required. Docker is required to run the LocalStack test; when Docker is unavailable, Testcontainers skips only that test.

```bash
# JVM resilience, AWS-emulation, and conditionally skipped observability tests
mvn -pl quality-engineering-tests -am test

# Python Lambda unit tests
python -m venv .venv
source .venv/bin/activate
pip install -r lambda/order-audit/requirements-dev.txt
pytest -q lambda/order-audit/tests
```

To verify the running services and the observability contract:

```bash
docker compose up -d --build
mvn -Denv=dev -pl services-tests/users-api-tests,services-tests/orders-api-tests -am test
mvn -pl quality-engineering-tests -Dtest=ObservabilitySmokeTest \
  -Dorders.baseUrl=http://localhost:8081 test
docker compose down -v
```

For complete commands, deployment prerequisites, expected behaviour, and limitations, see the [runbook](docs/RUNBOOK.md).

## Documentation Map

- [Framework guide](docs/FRAMEWORK-GUIDE.md): component boundaries, contracts, test coverage, and production hardening considerations.
- [Runbook](docs/RUNBOOK.md): local verification, CI gates, observability, infrastructure, Kubernetes, Pact, and performance commands.
- [Podman usage](.devcontainer/PODMAN.md): rootless Podman and Testcontainers socket configuration.

## Delivery Model

The GitHub Actions workflows run standalone Java tests, strict Qodana inspection analysis, Python linting/security/dependency audits and tests, Terraform formatting/validation, Gitleaks full-history secret verification, and Trivy dependency-vulnerability, secret, and IaC scanning on pushes and pull requests to `main`. The `containerised-api-tests` job runs the parent service API and observability checks when the parent service modules and a Compose file are present; in this standalone pack, it runs the in-repository HTTP component suites and LocalStack integration test instead. They do not deploy AWS resources, apply Kubernetes manifests, run k6, or invoke the Pact deployment gate; wire those steps into an environment-specific release pipeline after configuring credentials, state, image publishing, and Pact Broker access.

## Important Limits

- LocalStack and Moto validate client integration and handler behaviour, not all AWS IAM, quota, event-delivery, or service semantics.
- The Terraform files create an audit bucket and a Lambda execution role only. They do not package or create a Lambda function.
- The SAM template is a separate deployment route and currently relies on the Lambda runtime's bundled `boto3`; add a production `requirements.txt` if a pinned SDK version is required.
- Kubernetes manifests reference placeholder local image names and assume probe endpoints are enabled by the parent Spring Boot applications.
- The audit handler records an event; it is not a complete duplicate-delivery solution. See the [framework guide](docs/FRAMEWORK-GUIDE.md#serverless-audit-contract) before connecting it to an at-least-once event source.
