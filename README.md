# Pithos Vault

Secret management client implementations for the Pithos agent platform. Each module provides an async, Guice-injectable client for a specific secrets backend.

## Modules

### vault-api
Interface and abstract base for secret management. Java package: `info.pithos.vault`

| Type | Description |
|---|---|
| `VaultClient` | Interface extending `ServiceLifeCycle` — read, write, delete, and list secrets |
| `AbstractVaultClient` | Base class providing `submitAsync` via the platform `ForkJoinExecutor` and `createSecretPath` for request-context-aware key namespacing |

Operations:

| Group | Methods |
|---|---|
| Read | `getSecret` (latest), `getSecret` (versioned), `getSecretBytes` (latest), `getSecretBytes` (versioned) |
| Write | `setSecret`, `setSecretBytes` |
| Delete | `deleteSecret` |
| Query | `secretExists`, `listSecrets` |

Secret names are automatically namespaced by tenant/service using `Util.createKey(requestContext, name)`, matching the same namespacing convention used across the data layer.

### vault-hashicorp
HashiCorp Vault implementation of `VaultClient`. Java package: `info.pithos.vault.hashicorp`

Uses the `bettercloud/vault-java-driver` to communicate with a HashiCorp Vault server over its HTTP API. Secrets are stored in a KV v1 mount under `{mountPath}/{namespace}/{name}` with a single `value` field. Token authentication is used; the token is supplied via config.

`getSecret(rc, name, version)` delegates to the unversioned overload — KV v1 does not support versioning.

Config proto: `HashiCorpVaultConfigs` (`address`, `token`, `namespace`, `mountPath`, `timeoutMs`)

`mountPath` defaults to `"secret"` when not set.

### vault-gcp
GCP Secret Manager implementation of `VaultClient`. Java package: `info.pithos.vault.gcp`

Uses the `google-cloud-secretmanager` client library. Each logical secret maps to a GCP Secret Manager secret resource; `setSecret` / `setSecretBytes` transparently creates the secret if it does not exist, then adds a new version. Application Default Credentials are used for authentication.

Secret IDs are sanitized from the namespaced path (replacing characters outside `[a-zA-Z0-9_-]` with `_`) to meet GCP's secret ID constraints.

Config proto: `GcpSecretManagerConfigs` (`projectId`)

---

## Metrics

Every vault operation emits infra-tier metrics automatically via `MetricsCommitter`. No caller instrumentation required.

### VaultOperation

| Enum value | Metric stem |
|---|---|
| `GET_SECRET` | `vault.get.secret` |
| `SET_SECRET` | `vault.set.secret` |
| `DELETE_SECRET` | `vault.delete.secret` |
| `SECRET_EXISTS` | `vault.secret.exists` |
| `LIST_SECRETS` | `vault.list.secrets` |

`GET_SECRET` covers both string and bytes variants — the delegation pass-throughs (`getSecret(rc, name)` → versioned, `setSecret` → `setSecretBytes` in GCP) are not instrumented separately to avoid double-counting.

### What is emitted per operation

Each operation fires **4 metric events** across two levels:

| Level | `componentId` | Example |
|---|---|---|
| Per secret | Secret name passed to the operation | `"stripe-api-key"` |
| Provider aggregate | Provider name | `"gcp-secretmanager"` or `"hashicorp-vault"` |

At each level:
1. `{op}.latency` — `MetricUnit.MS`
2. `{op}.success` / `{op}.failure` / `{op}.timeout` — `MetricUnit.COUNT`

| Field | Value |
|---|---|
| `componentType` | `VAULT` |
| `componentId` | `name` parameter (for `listSecrets`: the prefix, or `"*"` when listing all) |
| `componentProvider` | `"gcp-secretmanager"` or `"hashicorp-vault"` |
| `RequestContext` | passed through from the caller |

### Example: `getSecret(rc, "stripe-api-key")` on GCP Secret Manager

| metric | unit | componentId | componentProvider |
|---|---|---|---|
| `vault.get.secret.latency` | MS | `stripe-api-key` | `gcp-secretmanager` |
| `vault.get.secret.success` | COUNT | `stripe-api-key` | `gcp-secretmanager` |
| `vault.get.secret.latency` | MS | `gcp-secretmanager` | `gcp-secretmanager` |
| `vault.get.secret.success` | COUNT | `gcp-secretmanager` | `gcp-secretmanager` |

### componentProvider values

| Implementation | `componentProvider` |
|---|---|
| `GcpSecretManagerClient` | `"gcp-secretmanager"` |
| `HashiCorpVaultClient` | `"hashicorp-vault"` |

---

## Usage

All clients follow the same lifecycle pattern used across the Pithos data layer:

```java
// 1. Create and initialise the Guice module
HashiCorpVaultModule module = new HashiCorpVaultModule(applicationContext);
module.init();

// 2. Create an injector
Injector injector = Guice.createInjector(module);

// 3. Start the client before use
VaultClient client = injector.getInstance(VaultClient.class);
client.start(10, TimeUnit.SECONDS).join();

// 4. Use the client
client.setSecret(requestContext, "api-key", "s3cr3t").join();

client.getSecret(requestContext, "api-key")
      .thenAccept(value -> System.out.println("secret: " + value));

client.listSecrets(requestContext, "")
      .thenAccept(names -> names.forEach(System.out::println));

// 5. Shut down gracefully
client.shutdown(10, TimeUnit.SECONDS).join();
```

Swap `HashiCorpVaultModule` for `GcpSecretManagerModule` to switch backends with no changes to call sites.

## Build

Requires JDK 23, Maven 3.9.x, and the `pithos-runtime-core-model` and `pithos-runtime-core-context` SNAPSHOTs installed locally. The `pithos-runtime-core-model` module must be rebuilt after the proto changes to regenerate `HashiCorpVaultConfigs` and `GcpSecretManagerConfigs`:

```bash
mvn install -f ../runtime-model/pom.xml
mvn compile          # compile all vault modules
mvn install          # install all vault modules to local Maven repository
```

## Dependencies

| Dependency | Version |
|---|---|
| `vault-java-driver` (bettercloud) | 5.1.0 |
| `google-cloud-secretmanager` | 2.46.0 |
| `guice` | 7.0.0 |
| `slf4j-api` | 2.0.16 |
