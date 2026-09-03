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
- Maven 3.9.0 or later, installed on the system (`mvn`)

This repository does not include a Maven wrapper. All documented commands use the
system Maven installation.

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
- `testcontainers/` — Source-only, unpublished standalone module (built separately in CI)

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

## Release Prerequisites

Releases publish the parent POM, core client, and Spring Boot starter through the
Sonatype Central Portal. Before a release tag is pushed:

- The `dev.streamline` namespace must already be verified in Central Portal.
- A Central Portal user token must be stored as the `CENTRAL_USERNAME` and
  `CENTRAL_PASSWORD` GitHub Actions secrets.
- An ASCII-armored private GPG key and its passphrase must be stored as
  `MAVEN_GPG_PRIVATE_KEY` and `MAVEN_GPG_PASSPHRASE`.
- No token, private key, or passphrase belongs in the repository.
- The tag must exactly equal `v` plus the Maven project version.
- The `STREAMLINE_CONFORMANCE_IMAGE_DIGEST` repository variable must hold an
  explicit, immutable image reference (`ghcr.io/streamlinelabs/streamline@sha256:<64
  hex chars>`). There is no default; the release workflow hard-blocks rather than
  falling back to a mutable tag such as `:latest`.

The tag workflow (`.github/workflows/release.yml`) enforces the following order, and
tagged publication cannot skip ahead:

1. **Live conformance** (`conformance` job) runs the full integration suite
   (`.github/workflows/integration.yml`) against the exact digest in
   `STREAMLINE_CONFORMANCE_IMAGE_DIGEST`. `scripts/require-image-digest.sh`
   hard-blocks the run if that variable is unset or not pinned by digest, and
   `scripts/verify-executed-tests.sh` hard-blocks it if the Failsafe reports show
   zero executed (i.e. all-skipped) tests — both failure modes stop the `publish`
   job from ever starting (`needs: conformance`).
2. **Build, test, sign, and SBOM generation** run to completion (`mvn verify -P
   release`) — this step never invokes Maven's `deploy` phase.
3. **Artifact and SBOM verification** (`scripts/verify-release-artifacts.sh`) confirms
   every expected jar and the CycloneDX SBOM exist and are non-empty.
4. **GitHub provenance and SBOM attestations** (`actions/attest`) run only after step 3
   passes, against the exact jars produced in step 2.
5. **Maven Central and GitHub publication are gated closed**
   (`scripts/central-publish-gate.sh`) with no environment-variable bypass. A
   plain `mvn deploy` re-runs the full lifecycle from a fresh
   JVM and is not guaranteed to reproduce byte-identical jars (no
   `project.build.outputTimestamp`/reproducible-build configuration exists yet), so
   auto-publishing immediately after attestation could release artifacts that do not
   match what was attested. The gate — and the corresponding
   `central-publishing-maven-plugin` `autoPublish=false` setting in `pom.xml` — keep
   Central publication blocked until a deploy mechanism that republishes the exact
   already-attested files is implemented and deliberately reviewed.

For an equivalent local release, pass the exact tag explicitly to
`make release`; the target validates the tag, runs the full
build and artifact/SBOM checks, and then intentionally stops at the same
fail-closed publication gate. It contains no deploy command.

## Reporting Issues

- Use the **Bug Report** or **Feature Request** issue templates
- Search existing issues before creating a new one
- Include reproduction steps for bugs

## Code of Conduct

All contributors are expected to follow our [Code of Conduct](https://github.com/streamlinelabs/.github/blob/main/CODE_OF_CONDUCT.md).

## License

By contributing, you agree that your contributions will be licensed under the Apache-2.0 License.
