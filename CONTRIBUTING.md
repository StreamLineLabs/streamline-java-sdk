# Contributing to Streamline Java SDK

Thank you for your interest in contributing to the Streamline Java SDK! This guide will help you get started.

## Getting Started

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Make your changes
4. Run tests and linting
5. Commit your changes (`git commit -m "Add my feature"`)
6. Push to your fork (`git push origin feature/my-feature`)
7. Open a Pull Request

## Prerequisites

- Java 17 or later
- Maven 3.8+ (or use the included `./mvnw` wrapper)

## Development Setup

```bash
# Clone your fork
git clone https://github.com/<your-username>/streamline-java-sdk.git
cd streamline-java-sdk

# Build the project
mvn compile

# Run tests
mvn verify
```

## Project Structure

This is a multi-module Maven project:

- `streamline-client/` — Core Java client library
- `streamline-spring-boot-starter/` — Spring Boot auto-configuration starter
- `examples/` — Runnable examples, compiled by every build but never published
- `testcontainers/` — Standalone Testcontainers module (own coordinates, built separately)

## Running Tests

Tests are split into two phases:

| Phase | Naming | Runner | Needs a server |
|---|---|---|---|
| Unit | `*Test` | Surefire | No |
| Integration | `*IT` (tagged `integration`) | Failsafe | Yes |

Unit tests are hermetic: they must never depend on a service running on the machine.
Where a real client object is needed, point it at
`UnitTestEndpoints.BOOTSTRAP_SERVERS` (TEST-NET-1, guaranteed unroutable) so the
result cannot change depending on whether a Streamline server happens to be running
locally. Binding an in-process stub to an ephemeral loopback port is fine — that is
still self-contained.

```bash
# Unit tests only — no server required
mvn test

# Compile, unit test, package and run SpotBugs — still no server required
mvn verify

# Run a specific test class
mvn test -pl streamline-client -Dtest=ProducerTest
```

### Integration Tests

Integration tests are opt-in and require a running Streamline server:

```bash
# Start the server (STREAMLINE_IMAGE overrides the image)
docker compose -f docker-compose.test.yml up -d

# Run everything, including *IT
STREAMLINE_INTEGRATION=1 mvn verify -Pintegration

# Stop the server
docker compose -f docker-compose.test.yml down -v
```

Selection rules:

- Without `-Pintegration` (and without `STREAMLINE_INTEGRATION=1`), Failsafe is
  skipped entirely, so `mvn verify` is self-contained and bounded.
- With the profile but without `STREAMLINE_INTEGRATION=1`, the `*IT` suites are
  reported as skipped.
- With `STREAMLINE_INTEGRATION=1`, an unreachable endpoint fails the build within
  seconds — integration tests never pass silently because a server was missing.

Endpoints are configurable through `STREAMLINE_BOOTSTRAP_SERVERS`,
`STREAMLINE_HTTP_URL` and `STREAMLINE_SCHEMA_REGISTRY_URL`; see
`dev.streamline.testsupport.IntegrationEnvironment`.

The `testcontainers/` module is built separately and follows the same rules:

```bash
cd testcontainers && STREAMLINE_INTEGRATION=1 mvn verify -Pintegration
```

## Code Style

- Follow standard Java conventions and existing code patterns
- Use meaningful variable and method names
- Add Javadoc for public APIs
- Keep methods focused and short

## Pull Request Guidelines

- Write clear commit messages
- Add tests for new functionality
- Update documentation if needed
- Ensure `mvn verify` passes before submitting (it must not need a server)

## Reporting Issues

- Use the **Bug Report** or **Feature Request** issue templates
- Search existing issues before creating a new one
- Include reproduction steps for bugs

## Code of Conduct

All contributors are expected to follow our [Code of Conduct](https://github.com/streamlinelabs/.github/blob/main/CODE_OF_CONDUCT.md).

## License

By contributing, you agree that your contributions will be licensed under the Apache-2.0 License.
