# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).


## [Unreleased]

### Added
- Circuit breaker pattern (`CircuitBreaker.java`) with configurable thresholds and execute() wrapper
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
