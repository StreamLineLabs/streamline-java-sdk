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

## Running Tests

```bash
# Unit tests
mvn test

# Full verification (unit + integration tests)
mvn verify

# Run a specific test class
mvn test -pl streamline-client -Dtest=StreamlineProducerTest
```

### Integration Tests

Integration tests require a running Streamline server:

```bash
# Start the server
docker compose -f docker-compose.test.yml up -d

# Run integration tests
mvn verify -Pintegration

# Stop the server
docker compose -f docker-compose.test.yml down
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
- Ensure `mvn verify` passes before submitting

## Reporting Issues

- Use the **Bug Report** or **Feature Request** issue templates
- Search existing issues before creating a new one
- Include reproduction steps for bugs

## Code of Conduct

All contributors are expected to follow our [Code of Conduct](https://github.com/streamlinelabs/.github/blob/main/CODE_OF_CONDUCT.md).

## License

By contributing, you agree that your contributions will be licensed under the Apache-2.0 License.
