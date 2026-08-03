# Service Release Workflow

The release workflows use an immutable Git SHA as both the Pact participant version and the container image tag. They deploy only after compatibility, Kubernetes rollout, health, and service-operation checks succeed. Pact deployment state is recorded only after those checks complete.

## Required GitHub Environment Configuration

Create a `production` GitHub Environment with appropriate approval rules. Configure these secrets there:

| Secret | Purpose |
|---|---|
| `PACT_BROKER_BASE_URL` | PactFlow/Pact Broker HTTPS URL |
| `PACT_BROKER_TOKEN` | Read/write token for Pact publication, verification results, and deployment records |
| `KUBE_CONFIG_DATA` | Base64-encoded kubeconfig with deploy and rollout-status permissions |

Configure these environment variables:

| Variable | Purpose |
|---|---|
| `K8S_NAMESPACE` | Kubernetes namespace; defaults to `default` when absent |
| `INVENTORY_BASE_URL` | Reachable production inventory-service base URL |
| `ORDERS_BASE_URL` | Reachable production orders-service base URL |

The runner must be able to reach the Kubernetes API and service URLs. Use a self-hosted runner when either is private. The workflows publish images to GHCR, so the repository Actions settings must allow `GITHUB_TOKEN` package writes and the cluster must be permitted to pull the resulting images.

## Consumer Release

Run `Orders Consumer Release` for consumer version `C101`.

1. Builds and pushes `orders-service:C101`.
2. Runs `InventoryConsumerPactTest` against the Pact mock provider.
3. Publishes `orders-service:C101` with branch `main`.
4. Waits up to five minutes for the inventory provider verification result.
5. Runs `can-i-deploy orders-service C101 -> production`.
6. Deploys the immutable orders image and waits for its Kubernetes rollout.
7. Verifies health, Prometheus metrics, and an order-create operation.
8. Records `orders-service:C101` as deployed to `production`.

The provider verification is also triggered by `LSEG Quality Gates` after a main-branch Pact publication. The provider workflow publishes verification results for the immutable provider version; it does not record a production deployment until a real rollout succeeds.

## Provider Release

Run `Inventory Provider Release` for provider version `P201`.

1. Builds and pushes `inventory-service:P201`.
2. Retrieves the published `orders-service` contracts from the Pact Broker.
3. Starts the real `InventoryApplication` and applies `inventory reservation is evaluated` provider state before each interaction.
4. Verifies every retrieved interaction and publishes the result for `P201`.
5. Runs `can-i-deploy inventory-service P201 -> production`.
6. Deploys the immutable inventory image and waits for its Kubernetes rollout.
7. Verifies health and Prometheus metrics.
8. Records `inventory-service:P201` as deployed to `production`.

Deploy the provider before the consumer when a newly published consumer contract requires a provider version that is not already recorded in production.

## Rollback

Run `Orders Service Rollback` with the exact previous Pact version and immutable image, for example `C100` and `ghcr.io/example/orders-service:C100`.

1. The dispatcher must confirm database, event, and infrastructure compatibility.
2. The workflow runs `can-i-deploy orders-service C100 -> production`.
3. It deploys only that named image, waits for rollout, and verifies health, metrics, and an order operation.
4. It records `orders-service:C100` as deployed to `production`.
5. The workflow emits a post-rollback monitoring notice. Monitor errors, latency, queues, and traces in the production observability system before closing the incident.

The rollback workflow does not use `kubectl rollout undo`, because that command does not prove the restored image matches the Pact version being recorded.
