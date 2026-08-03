# Examples

Runnable examples for the Streamline Java SDK. They are compiled by every build
(`mvn verify`) so they cannot drift from the API, but they are never published.

## Prerequisites

- Java 17+, Maven
- A running Streamline server (default: `localhost:9092`)

## Running

Start Streamline:

```bash
# Via Docker Compose (STREAMLINE_IMAGE overrides the image)
docker compose -f ../docker-compose.test.yml up -d

# Or via Homebrew
streamline --playground
```

Run an example:

```bash
mvn compile exec:java -pl examples -Dexec.mainClass=dev.streamline.examples.BasicUsage
```

| Example | Description |
|---|---|
| `BasicUsage` | Produce, consume and commit |
| `AdminClientUsage` | Topics, consumer groups and cluster info |
| `QueryUsage` | StreamQL queries over the HTTP API |
| `SchemaRegistryUsage` | Schema registration, compatibility and produce/consume |
| `CircuitBreakerUsage` | Resilient production behind a circuit breaker |
| `SecurityUsage` | TLS and SASL configuration |
| `AgentMemoryUsage` | Agent memory remember/recall (experimental) |

## Configuration

All examples read their endpoints from the environment:

| Variable | Default | Purpose |
|---|---|---|
| `STREAMLINE_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka-protocol endpoint |
| `STREAMLINE_HTTP_URL` | `http://localhost:9094` | HTTP API endpoint |
| `STREAMLINE_SCHEMA_REGISTRY_URL` | `$STREAMLINE_HTTP_URL` | Schema Registry endpoint |

```bash
export STREAMLINE_BOOTSTRAP_SERVERS=my-server:9092
mvn compile exec:java -pl examples -Dexec.mainClass=dev.streamline.examples.BasicUsage
```
