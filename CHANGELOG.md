# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).


## [Unreleased]

### Changed
- **Test layout** — unit tests (`*Test`, Surefire) are now hermetic and never contact a
  broker or HTTP endpoint, so `mvn verify` is self-contained and bounded. Tests that need
  a live server moved to Failsafe integration tests (`*IT`, tagged `integration`):
  `ProducerIT`, `ConsumerIT`, `StreamlineIT`, `ConformanceIT` (formerly `ConformanceTest`)
  and `StreamlineContainerIT` (formerly `StreamlineContainerTest`).
- Integration tests are opt-in: they run only under `-Pintegration` (auto-activated by
  `STREAMLINE_INTEGRATION=1`) and require `STREAMLINE_INTEGRATION=1`. When enabled, an
  unreachable endpoint now fails fast instead of being silently skipped. Endpoints are
  configurable with `STREAMLINE_BOOTSTRAP_SERVERS`, `STREAMLINE_HTTP_URL` and
  `STREAMLINE_SCHEMA_REGISTRY_URL`.
- `docker-compose.test.yml` no longer pins an unpublished image tag; it uses
  `STREAMLINE_IMAGE` (default `ghcr.io/streamlinelabs/streamline:latest`) and the
  `/health/live` probe.
- Examples moved from the non-existent `com.streamline.*` API to the real
  `dev.streamline.*` API and are now compiled by every build as the `examples` module
  (never installed or deployed).
- Compiler now uses `release` instead of `source`/`target`, so building on a newer JDK
  cannot link against post-Java-17 APIs.

### Fixed
- `streamline-client` and `streamline-spring-boot-starter` declared parent version
  `0.2.0` while the parent POM was `0.3.0`, so the build silently resolved a stale
  installed parent (and failed outright on a clean machine).
- Spring Boot starter no longer fails to start in non-web applications: the
  `StreamlineTemplate` bean falls back to its own `ObjectMapper` when the application
  does not define one, and `StreamlineMetrics` is only created when a `MeterRegistry`
  bean exists.
- `AdminClient` branch operations and `QueryClient.explain` now carry per-request
  timeouts; `AdminClient` honours the configured connect/request timeouts.
- `StreamlineVerifier` checks for missing attestation fields instead of catching
  `NullPointerException`, and `CircuitBreaker` switches have explicit default branches.
- Static analysis runs again on modern JDKs (SpotBugs 4.9.x); documented exclusions live
  in `spotbugs-exclude.xml`.
- Mockito and Byte Buddy are managed explicitly so mocking works on current JDKs; the
  Spring Boot BOM previously pinned an unusable Byte Buddy version.


## [0.3.0] - 2026-04-20

### Added
- `dev.streamline.client.moonshot` package — HTTP clients for the Streamline
  Moonshot control plane (port `9094`):
  - `BranchesClient` (M5 — list / create / delete / merge branches)
  - `ContractsClient` (M4 — register / get / validate JSON-Schema contracts)
  - `AttestationClient` (M4 — request signatures, verify them)
  - `SearchClient` (M2 — semantic search across topics)
  - `MemoryClient` (M1 — agent memory remember / recall)
- Spring Boot starter (`streamline-spring-boot-starter`) auto-wires the five
  moonshot clients when `streamline.moonshot.http-url` is set.

### Added
- Circuit breaker pattern (`CircuitBreaker.java`) with configurable thresholds and execute() wrapper
- Circuit breaker integration in `Producer` — automatically checks CB before send, records success/failure
- Circuit breaker usage example (`CircuitBreakerUsage.java`)
- TLS/SASL authentication example (`SecurityUsage.java`)
- CircuitBreaker test suite (14 tests covering state transitions, thresholds, error classification)
- `isRetryable()` method on `StreamlineException` for circuit breaker integration

### Fixed
- Producer now wires `compressionType` and `idempotent` settings to Kafka ProducerConfig
- Producer now wires `retries`, `retryBackoffMs`, and `maxRequestSize` to Kafka ProducerConfig

### Changed
- fix: resolve thread safety issue in producer pool (2026-03-06)
- feat: add schema registry usage example (2026-03-06)
- **Changed**: update Kafka client dependency to 3.7
- **Testing**: add Testcontainers integration test suite
- **Changed**: simplify Maven module dependency tree
- **Fixed**: resolve thread safety issue in consumer registry
- **Added**: add Spring Boot auto-configuration for producer

### Fixed
- Correct deserialization of nullable fields

### Changed
- Extract common config into shared module

### Performance
- Optimize batch producer flush strategy


## [0.2.0] - 2026-02-18

### Added
- Multi-module Maven project (core client + Spring Boot starter)
- `StreamlineClient` with builder pattern
- `Producer` and `Consumer` with Kafka protocol support
- `Admin` client for topic management
- Spring Boot 3.2.0 auto-configuration with `@ConditionalOnClass`
- `@StreamlineListener` annotation for declarative event handling
- Exception hierarchy with error codes and hints
- Testcontainers integration for testing

### Infrastructure
- CI pipeline with build, test, and code coverage
- CodeQL security scanning
- Release workflow with Maven Central publishing
- Release drafter for automated release notes
- Dependabot for dependency updates
- CONTRIBUTING.md with development setup guide
- Security policy (SECURITY.md)
- EditorConfig for consistent formatting
- Issue templates for bug reports and feature requests

## [0.1.0] - 2026-02-18

### Added
- Initial release of Streamline Java SDK
- Core client library with Kafka protocol compatibility
- Spring Boot starter module
- Testcontainers support for integration testing
- Apache 2.0 license
- test: add BatchAccumulator unit test scaffolding
- test: add WireFormat serialization test coverage
- test: add BatchAccumulator edge case and boundary tests
