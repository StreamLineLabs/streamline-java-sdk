# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
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

