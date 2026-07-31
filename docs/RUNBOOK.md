# Runbook

Run the quality-test and Lambda commands from this repository root. Run parent-service commands after copying the pack into the parent framework and applying `existing-repo-fixes.patch`.

## Prerequisites

| Capability | Required Tooling |
|---|---|
| JVM tests | Java 17 and Maven |
| LocalStack test and parent services | Docker Desktop or Docker Engine with Compose |
| Lambda unit tests | Python 3.12, `pip`, and `pytest` |
| Terraform validation or apply | Terraform 1.6 or newer and AWS credentials for apply |
| Kubernetes deployment | `kubectl` with target-cluster access |
| Performance smoke | k6 |
| Pact compatibility gate | `pact-broker` CLI and Pact Broker credentials |
| SAM deployment | AWS SAM CLI and AWS credentials |

Podman can replace Docker for the container workflows. Configure Testcontainers with the Podman socket as described in [Podman usage](../.devcontainer/PODMAN.md).

## JVM Tests

```bash
# All enhancement JVM tests; LocalStack skips when no Docker daemon is available.
mvn -pl quality-engineering-tests -am test

# A focused resilience example.
mvn -pl quality-engineering-tests -Dtest=CircuitBreakerBehaviourTest test

# Run the observability test against the running orders service.
mvn -pl quality-engineering-tests -Dtest=ObservabilitySmokeTest \
  -Dorders.baseUrl=http://localhost:8081 test
```

The observability test is intentionally skipped without `orders.baseUrl`; this makes the normal JVM suite runnable without starting the application. The LocalStack test is an emulator test, not a substitute for an AWS environment test.

## Parent Service And API Checks

```bash
docker compose up -d --build

# Both health endpoints should return 200 before API tests begin.
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8081/actuator/health

mvn -Denv=dev \
  -pl services-tests/users-api-tests,services-tests/orders-api-tests -am test

docker compose down -v
```

The CI workflow waits for these endpoints before invoking the API suites. Retain `docker compose logs` on failures; it is the shortest path to distinguish a product failure from a startup/configuration failure.

## Python Lambda Tests

```bash
python -m venv .venv
source .venv/bin/activate
python -m pip install -r lambda/order-audit/requirements-dev.txt
pytest -q lambda/order-audit/tests
```

The tests use Moto and do not call AWS. Set `AUDIT_BUCKET` only when invoking the handler outside the test harness. The handler needs credentials with `s3:PutObject` permission to `orders/*` for the target bucket.

## SAM

Validate the template before deployment:

```bash
sam validate --template-file lambda/order-audit/template.yaml
```

The template can be deployed with standard SAM commands after an AWS account, region, stack name, and production dependency policy have been chosen. Its only dependency list is `requirements-dev.txt`; add a pinned production `requirements.txt` before treating `sam build` and `sam deploy` as reproducible release steps.

## Terraform

```bash
cd infrastructure/terraform
terraform init
terraform fmt -check -recursive
terraform validate
terraform plan -var='environment=dev' -out=tfplan
terraform apply tfplan
```

The bucket name contains the AWS account ID and the selected environment. Configure a remote state backend, encryption, and access controls before using `apply` for a shared environment. The module creates no Lambda function; its `lambda_role_arn` output is an example hand-off value, not an active integration.

Destroy only an environment that this configuration owns and only after confirming the audit-data retention requirement:

```bash
terraform destroy -var='environment=dev'
```

## Observability Stack

Start the parent services first, then run the optional monitoring stack:

```bash
docker compose up -d --build
docker compose -f observability/docker-compose.observability.yml up -d
```

- Prometheus UI: `http://localhost:9090`
- Jaeger UI: `http://localhost:16686`

Prometheus expects the services on host ports 8080 and 8081. On Linux Docker Engine, replace or supplement `host.docker.internal` with a configured host gateway before relying on the supplied scrape targets. The services must be configured to emit Prometheus metrics and OTLP traces; starting Jaeger alone does not create traces.

Stop the stacks when finished:

```bash
docker compose -f observability/docker-compose.observability.yml down -v
docker compose down -v
```

## Kubernetes

Render the manifest before connecting it to a cluster:

```bash
kubectl kustomize k8s
```

Publish or load service images that the target cluster can pull, then replace `sample/users-service:local` and `sample/orders-service:local` with immutable image references. After verifying the parent applications expose the probe paths, deploy and inspect rollout status:

```bash
kubectl apply -k k8s
kubectl rollout status deployment/users-service
kubectl rollout status deployment/orders-service
```

The resources are namespace-agnostic and services are `ClusterIP` by default. Provide a namespace, ingress, secrets, image pull configuration, network policy, and environment-specific resource sizing outside this illustrative base.

## Performance Smoke Test

With orders available on port 8081:

```bash
k6 run performance/k6/orders-load.js

# A non-default target.
ORDERS_BASE_URL=https://orders.example.internal k6 run performance/k6/orders-load.js
```

The scenario ramps to 10 virtual users, holds for 40 seconds, then ramps down. It fails when HTTP or business-response errors reach 1 percent, p95 reaches 500 ms, or p99 reaches 1 second. Treat these as a smoke-test baseline; select environment-specific thresholds only after collecting representative telemetry.

## Pact Deployment Gate

Install the Pact Broker CLI, then supply a released application version and the target deployment environment:

```bash
export PACT_BROKER_BASE_URL=https://your-broker
export PACT_BROKER_TOKEN=...
export PACTICIPANT=orders-service
export APPLICATION_VERSION="$(git rev-parse HEAD)"
export TARGET_ENVIRONMENT=production
./scripts/can-i-deploy.sh
```

The script exits non-zero if compatibility is unknown or rejected after its configured retries. It is a release compatibility control, not evidence of functional, security, performance, resilience, or infrastructure correctness.

## CI Gate Coverage

`.github/workflows/lseg-quality-gates.yml` runs on pushes and pull requests to `main`:

| Job | Check |
|---|---|
| `java-fast-tests` | Enhancement JVM tests and Surefire report upload |
| `containerised-api-tests` | Package services, start Compose, health checks, API tests, and observability smoke test |
| `python-lambda-tests` | Pytest/Moto Lambda tests |
| `terraform-static-validation` | Terraform formatting, backend-free initialization, and validation |

The Java, Lambda, and Terraform jobs run in this repository. The containerised job detects whether the parent framework's Maven modules and Compose file exist, and emits a successful not-applicable result when they do not. Configure branch protection to require the jobs that apply to your release process. Add separate release jobs for image publication, real AWS checks, `terraform plan/apply`, Kubernetes rollout verification, k6, and `can-i-deploy`; those actions require environment-specific credentials and approval controls.
