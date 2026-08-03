# Clean Code and SRP Audit

## Summary

- **Highest-leverage future split:** separate HTTP request mechanics from the
  500-line `SchemaRegistryClient`, preserving its public synchronous API.
- Producer, consumer, and main client are stateful lifecycle actors; splitting
  them without stronger concurrency coverage risks hidden ordering changes.
- `AdminClient` spans topic/group/cluster operations but shares one Kafka admin
  backend and has bounded methods; another facade would add indirection.
- Spring listener discovery and listener execution share one processor because
  lifecycle state connects them; a split is only useful after startup/shutdown
  behavior is more deeply characterized.
- The baseline repair now compiles examples and cleanly separates 336 unit
  tests from 58 opt-in integration tests.

## Findings

| ID | Location | Category | Severity | Actors in conflict | Cost | Size | Behavior risk |
|---|---|---|---|---|---|---|---|
| JAVA-SRP-1 | `schema/SchemaRegistryClient.java` | SRP, HTTP client | P2 | Schema Registry endpoint policy; HTTP transport/auth/cache | Repeated request/status/decoding mechanics obscure endpoint-specific rules. | L | Medium |
| JAVA-SRP-2 | `testcontainers/StreamlineContainer.java` | SRP, test product | P2 | container lifecycle; endpoint helpers; topic/test utility methods | Docker lifecycle and SDK-specific convenience operations change for different test actors. | L | Medium |
| JAVA-CC-1 | `StreamlineAutoConfiguration`/listener processor | Spring lifecycle warning | P2 | auto-configuration; listener discovery | BeanPostProcessor construction eagerly creates configuration/client beans, producing Spring eligibility warnings. | M | Medium |

## Ordered Refactor Sequence

1. Characterize Schema Registry request paths, bodies, auth, statuses, and
   decoding with an in-process HTTP server.
2. Extract a private request executor; keep endpoint policy in public methods.
3. Add listener-processor tests for lazy client resolution, bean discovery,
   start, stop, and shutdown.
4. Only then remove eager BeanPostProcessor dependencies.
5. Keep producer/consumer state intact until race/lifecycle coverage improves.

## Deferred

- Schema Registry transport extraction needs broader endpoint tests.
- Listener processor cleanup needs Spring lifecycle characterization.
- Live integration remains blocked by registry access.

## Out of Scope

- `StreamlineConfig`/`StreamlineProperties`: public configuration contracts.
- Producer and consumer: cohesive stateful actors.
- `AdminClient`: one Kafka administration backend.
- Examples: compiled documentation product, intentionally separate module.
