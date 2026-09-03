# Clean Code and SRP Audit

## Release-Gating Hardening — 2026-09-03 (later pass)

Closed the two remaining release-safety gaps this repository's own audit history
had flagged as manual/outstanding blockers:

1. **Tagged publication now depends on live conformance against an explicit,
   immutable image digest.** `.github/workflows/integration.yml` gained a
   `resolve-image` job that hard-blocks (`scripts/require-image-digest.sh`) unless
   the image resolves to `repository@sha256:<64 hex chars>` — an absent value, a bare
   tag, or a mutable tag such as `:latest` are all rejected, never silently
   substituted. `docker pull ghcr.io/streamlinelabs/streamline:latest` and `:0.3.0`
   were both confirmed to return `manifest unknown` in this environment, so the prior
   `:latest`-defaulting workflow input was not a hypothetical risk. Both `integration`
   and `testcontainers` jobs now also run `scripts/verify-executed-tests.sh` against
   their Failsafe reports, hard-failing if the discovered test count is zero or every
   discovered test was skipped (the exact failure mode `STREAMLINE_INTEGRATION` being
   left unexported produces). `.github/workflows/release.yml` calls this hardened
   workflow via `workflow_call` and its `publish` job declares `needs: conformance`,
   so a tagged push cannot reach the publish job without a passing, digest-pinned,
   test-count-guarded live conformance run. No real Streamline image digest exists in
   this sandboxed org, so the *positive* (image reachable, tests pass) path could not
   be exercised end-to-end here; the *negative* (absent/mutable image, and
   zero-executed-test) hard-block paths were exercised directly against the new
   scripts and via `make integration-test`/`docker compose config`.
2. **Maven Central can no longer auto-publish ahead of verification or
   attestation.** The previous `release.yml` ran `mvn deploy -P release` (which
   uploads *and*, via `autoPublish=true`, immediately releases to Central) before
   `scripts/verify-release-artifacts.sh` and before either `actions/attest` step —
   so a build could publish to Central and only afterwards discover a missing SBOM
   or artifact. The workflow is reordered so build/test/sign/SBOM-generation
   (`mvn verify -P release`, not `deploy`) happens first, then artifact/SBOM
   verification, then both attestations, and only then is Central publication even
   considered. `pom.xml`'s `central-publishing-maven-plugin` now sets
   `autoPublish=false`, and `scripts/central-publish-gate.sh` fails closed
   unconditionally — Central Portal and GitHub publication stay blocked until a
   deploy mechanism that republishes the exact already-attested `target/*.jar`
   bytes (not a rebuilt copy) is deliberately implemented in source. There is
   no environment-variable bypass. `Makefile`'s `release` target
   had the same problem locally (`mvn deploy -P release -DskipTests` with no
   verification at all) and now runs verify → check-artifacts → hard-block,
   with no deploy command. No publish, upload, tag, or credential use was attempted.

Focused tests were added for every new guard: `scripts/test-require-image-digest.sh`,
`scripts/test-verify-executed-tests.sh`, and `scripts/test-central-publish-gate.sh`,
alongside the pre-existing `scripts/test-validate-release-tag.sh` and
`scripts/test-release-preflight.sh`; all five now run in `ci.yml`. `actionlint`
reports zero findings across all five workflow files.

## Release Readiness Update — 2026-09-03

Follow-up verification pass: re-ran `mvn verify` (root reactor, 350 tests),
`mvn -f testcontainers/pom.xml verify`, the release-profile package/verify dry
run (`-P release -DskipTests -Dgpg.skip=true`), `scripts/verify-release-artifacts.sh`,
and both `scripts/test-*.sh` self-tests — all pass. One additional public-facing
gap was found and fixed: `examples/.../QueryUsage.java` built a record value with
manual `String.format("{...}")` JSON; it now uses the same Jackson
`ObjectMapper`/`Map.of` pattern already applied to the SDK's own HTTP clients, so
the example teaches the same safe-construction convention it demonstrates.
No other manual JSON/URL-encoding gaps were found (`URLEncoder`, ad hoc
`String.format` JSON bodies, and un-encoded dynamic path segments are gone from
`streamline-client` and `examples`). Live integration (`*IT`, `-Pintegration`)
remains blocked in this environment: the local Docker daemon is not running, so
`docker-compose.test.yml` cannot start a server; `IntegrationEnvironment` already
fails closed (skip only when explicitly disabled, hard failure otherwise) so CI
cannot silently pass a broken integration path, but the live conformance/IT suite
itself was not exercised here for lack of a reachable server.

## Release Readiness Update — 2026-09-02

Programmatic P0/P1 remediation is complete in the working tree:

- HTTP JSON bodies use Jackson and dynamic path segments use strict RFC 3986
  UTF-8 encoding with hostile-input regression coverage.
- Release tags and required Central Portal/GPG secrets fail closed before
  credential import, signing, or deployment.
- Publishing uses Sonatype Central Portal rather than legacy OSSRH staging.
- The pinned CycloneDX build is mandatory, release artifacts are checked, and
  GitHub provenance plus SBOM attestations cover the releasable Maven artifacts.
- Unused Netty and core Micrometer runtime dependencies were removed, and the
  Spring module now depends on the precise optional Actuator API instead of its
  broader starter aggregator.
- The Spring configuration processor is isolated on the compiler annotation-processor
  path so JDK 23+ builds still generate metadata without exposing it to consumers.
- Maven 3.9.0+ is documented and enforced; no wrapper is claimed or fabricated.
- The standalone Testcontainers 0.2.0 module remains built/tested but is explicitly
  source-only and excluded from publication claims.

Remaining manual release blockers:

1. Verify the `dev.streamline` namespace in Sonatype Central Portal.
2. Configure the Central user-token and GPG GitHub Actions secrets documented in
   `CONTRIBUTING.md`.
3. Run the live Docker/server integration workflow against the intended release
   image.
4. Push the release tag only after those prerequisites are satisfied; no publish
   was attempted during this remediation.

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
- The baseline repair now compiles examples and cleanly separates 350 unit
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
