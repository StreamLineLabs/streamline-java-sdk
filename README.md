# Streamline Java SDK

[![CI](https://github.com/streamlinelabs/streamline-java-sdk/actions/workflows/ci.yml/badge.svg)](https://github.com/streamlinelabs/streamline-java-sdk/actions/workflows/ci.yml)
[![codecov](https://img.shields.io/codecov/c/github/streamlinelabs/streamline-java-sdk?style=flat-square)](https://codecov.io/gh/streamlinelabs/streamline-java-sdk)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://openjdk.org/)
[![Docs](https://img.shields.io/badge/docs-streamlinelabs.dev-blue.svg)](https://streamlinelabs.dev/docs/sdks/java)
[![Maven Central](https://img.shields.io/maven-central/v/dev.streamline/streamline-client.svg)](https://search.maven.org/artifact/dev.streamline/streamline-client)

Native Java client library for Streamline with Spring Boot integration.

## Modules

- **streamline-client**: Core Java client library
- **streamline-spring-boot-starter**: Spring Boot auto-configuration

## Quick Start

### Maven

```xml
<dependency>
    <groupId>dev.streamline</groupId>
    <artifactId>streamline-client</artifactId>
    <version>0.3.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'dev.streamline:streamline-client:0.3.0'
```

## Usage

### Basic Client

```java
import dev.streamline.client.Streamline;
import dev.streamline.client.StreamlineConfig;

// Create client
Streamline client = Streamline.builder()
    .bootstrapServers("localhost:9092")
    .build();

// Produce messages
client.produce("my-topic", "key", "Hello, Streamline!");

// Consume messages
try (var consumer = client.consumer("my-topic", "my-group")) {
    consumer.subscribe();
    while (true) {
        var records = consumer.poll(Duration.ofMillis(100));
        for (var record : records) {
            System.out.println(record.value());
        }
    }
}
```

### Spring Boot Integration

Add the starter dependency:

```xml
<dependency>
    <groupId>dev.streamline</groupId>
    <artifactId>streamline-spring-boot-starter</artifactId>
    <version>0.3.0</version>
</dependency>
```

Configure in `application.yml`:

```yaml
streamline:
  bootstrap-servers: localhost:9092
  producer:
    batch-size: 16384
    linger-ms: 1
  consumer:
    group-id: my-app
    auto-offset-reset: earliest
```

Use in your code:

```java
@Service
public class EventService {

    @Autowired
    private StreamlineTemplate streamline;

    public void publishEvent(String topic, Event event) {
        streamline.send(topic, event.getId(), event);
    }
}

@StreamlineListener(topics = "events", groupId = "my-service")
public void handleEvent(Event event) {
    // Process event
}
```

## Features

- **Fluent Builder API**: Easy-to-use configuration
- **Admin Client**: Topic management, consumer group inspection via HTTP REST API
- **SQL Queries**: Execute SQL queries against streaming data
- **Connection Pooling**: Automatic connection management
- **Automatic Reconnection**: Handles transient failures
- **Compression**: LZ4, Zstd, Snappy, Gzip
- **TLS/mTLS**: Secure connections with certificate support
- **SASL Authentication**: PLAIN and SCRAM-SHA-256/512
- **Metrics Integration**: Micrometer support
- **OpenTelemetry Tracing**: Optional distributed tracing for produce/consume operations
- **Spring Boot Auto-configuration**: Zero-config for Spring apps
- **Async Support**: CompletableFuture-based async operations

## OpenTelemetry Tracing

The SDK supports optional OpenTelemetry auto-instrumentation for produce and consume
operations. When `opentelemetry-api` is on the classpath, tracing is automatically
available. When it is absent, the tracing layer is a zero-overhead no-op.

### Setup

Add the OpenTelemetry dependency alongside the SDK:

```xml
<dependency>
    <groupId>dev.streamline</groupId>
    <artifactId>streamline-client</artifactId>
    <version>0.3.0</version>
</dependency>
<!-- Optional: enable OpenTelemetry tracing -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
    <version>1.34.0</version>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk</artifactId>
    <version>1.34.0</version>
</dependency>
```

### Usage

```java
import dev.streamline.client.telemetry.StreamlineTracing;

// Create tracing (auto-detects OTel on classpath)
StreamlineTracing tracing = StreamlineTracing.create();

// Wrap produce operations
RecordMetadata metadata = tracing.traceProducer("orders", headers, () -> {
    return producer.send("orders", key, value, headers).join();
});

// Wrap consume operations
List<ConsumerRecord<String, String>> records = tracing.traceConsumer("events", () -> {
    return consumer.poll(Duration.ofMillis(100));
});
```

### Span Conventions

All spans follow OTel semantic conventions for messaging:

| Attribute | Value |
|-----------|-------|
| Span name | `{topic} {operation}` (e.g., "orders produce") |
| `messaging.system` | `streamline` |
| `messaging.destination.name` | Topic name |
| `messaging.operation` | `produce`, `consume`, or `process` |
| Span kind | `PRODUCER` for produce, `CONSUMER` for consume |

Trace context is propagated through Kafka message headers for end-to-end
distributed tracing across producer and consumer.

## Requirements

- Java 17 or later
- Streamline server 0.3.0 or later

## Building from Source

```bash
mvn clean install
```

## Testing

Unit tests are hermetic — they never depend on a running broker or HTTP endpoint — so
the default build is self-contained and bounded:

```bash
mvn verify            # compile + unit tests + package + SpotBugs, no server needed
make unit-test        # unit tests only
```

Integration tests (`*IT`, including the conformance suite) need a live Streamline
server and are **opt-in**. They only run when the `integration` profile is active,
and they require `STREAMLINE_INTEGRATION=1` — without it they are skipped, and with
it an unreachable server fails the build instead of silently passing:

```bash
docker compose -f docker-compose.test.yml up -d
STREAMLINE_INTEGRATION=1 mvn verify -Pintegration
docker compose -f docker-compose.test.yml down -v

# or, all of the above:
make integration-test
```

| Variable | Default | Purpose |
|---|---|---|
| `STREAMLINE_INTEGRATION` | *(unset)* | Set to `1` to enable integration tests |
| `STREAMLINE_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka-protocol endpoint |
| `STREAMLINE_HTTP_URL` | `http://localhost:9094` | HTTP API endpoint |
| `STREAMLINE_SCHEMA_REGISTRY_URL` | `$STREAMLINE_HTTP_URL` | Schema Registry endpoint |
| `STREAMLINE_IMAGE` | `ghcr.io/streamlinelabs/streamline:latest` | Image used by Docker Compose and Testcontainers |

Exporting `STREAMLINE_INTEGRATION=1` also activates the `integration` profile on its
own, so `mvn verify` is enough once it is set.

## API Reference

### Client

| Method | Description |
|--------|-------------|
| `Streamline.builder()` | Create a new client builder |
| `client.produce(topic, key, value)` | Send a message synchronously |
| `client.produce(topic, key, value, headers)` | Send a message with headers |
| `client.produceAsync(topic, key, value)` | Send a message asynchronously |
| `client.isHealthy()` | Check client health status |
| `client.close()` | Close the client connection |

### Producer

| Method | Description |
|--------|-------------|
| `producer.send(topic, key, value)` | Send a message to a topic |
| `producer.send(topic, key, value, headers)` | Send a message with headers |
| `producer.send(topic, partition, key, value)` | Send to a specific partition |
| `producer.flush()` | Flush buffered messages |
| `producer.close()` | Close the producer |

### Consumer

| Method | Description |
|--------|-------------|
| `consumer.subscribe()` | Subscribe to configured topics |
| `consumer.commitSync()` | Commit offsets synchronously |
| `consumer.commitAsync()` | Commit offsets asynchronously |
| `consumer.seekToBeginning()` | Seek to start of partition |
| `consumer.seekToEnd()` | Seek to end of partition |
| `consumer.seek(partition, offset)` | Seek to a specific offset |
| `consumer.position(partition)` | Get current position |
| `consumer.pause()` | Pause consuming |
| `consumer.resume()` | Resume consuming |
| `consumer.close()` | Close the consumer |

### Admin Client

| Method | Description |
|--------|-------------|
| `admin.listTopics()` | List all topics |
| `admin.describeTopic(name)` | Get topic details |
| `admin.createTopic(name, partitions)` | Create a new topic |
| `admin.deleteTopic(name)` | Delete a topic |
| `admin.listConsumerGroups()` | List consumer groups |
| `admin.describeConsumerGroup(id)` | Get group details |

### Query Client

| Method | Description |
|--------|-------------|
| `query.query(sql)` | Execute a SQL query against stream data |
| `query.query(sql, timeoutMs, maxRows)` | Execute a query with an explicit bound |
| `query.explain(sql)` | Return the query plan |

## Error Handling

```java
import dev.streamline.client.StreamlineException;

try {
    client.produce("my-topic", "key", "value");
} catch (StreamlineException e) {
    System.out.println("Error code: " + e.getErrorCode());
    System.out.println("Hint: " + e.getHint());
    if (e.isRetryable()) {
        System.out.println("Retryable error: " + e.getMessage());
    } else {
        System.out.println("Fatal error: " + e.getMessage());
    }
}
```

## Configuration Reference

### Client

| Parameter | Default | Description |
|---|---|---|
| `bootstrap-servers` | `localhost:9092` | Comma-separated list of Streamline broker addresses |
| `client-id` | auto-generated | Client identifier for server-side logging |

### Producer

| Parameter | Default | Description |
|---|---|---|
| `batch-size` | `16384` | Maximum batch size in bytes before flushing |
| `linger-ms` | `0` | Time to wait for additional messages before sending a batch |
| `compression-type` | `none` | Compression codec: `none`, `gzip`, `snappy`, `lz4`, `zstd` |
| `acks` | `1` | Acknowledgment level: `0` (none), `1` (leader), `-1` (all replicas) |
| `retries` | `3` | Number of retries on transient failures |
| `enable.idempotence` | `false` | Enable exactly-once semantics |

### Consumer

| Parameter | Default | Description |
|---|---|---|
| `group-id` | *(required)* | Consumer group identifier |
| `auto-offset-reset` | `latest` | Where to start when no committed offset exists: `earliest`, `latest` |
| `enable-auto-commit` | `true` | Automatically commit offsets after polling |
| `auto-commit-interval-ms` | `5000` | Interval between auto-commits in milliseconds |
| `max-poll-records` | `500` | Maximum records returned per poll |
| `session-timeout-ms` | `30000` | Session timeout for consumer group membership |

### Security

| Parameter | Default | Description |
|---|---|---|
| `security-protocol` | `PLAINTEXT` | Protocol: `PLAINTEXT`, `SSL`, `SASL_PLAINTEXT`, `SASL_SSL` |
| `sasl-mechanism` | — | SASL mechanism: `PLAIN`, `SCRAM-SHA-256`, `SCRAM-SHA-512` |
| `ssl-truststore-location` | — | Path to TLS trust store |

## Circuit Breaker

Protect your application from cascading failures when the Streamline server is unresponsive:

```java
import dev.streamline.client.CircuitBreaker;

CircuitBreaker breaker = new CircuitBreaker(
    CircuitBreaker.Config.builder()
        .failureThreshold(5)       // Open after 5 consecutive failures
        .successThreshold(2)       // Close after 2 half-open successes
        .openTimeout(Duration.ofSeconds(30))
        .onStateChange((from, to) -> log.info("Circuit: {} → {}", from, to))
        .build()
);

// Wrap producer calls
RecordMetadata result = breaker.execute(() ->
    producer.send("events", "user-1", payload).get()
);
```

When the circuit is open, `execute()` throws a retryable `StreamlineException`. See the [Circuit Breaker guide](https://streamlinelabs.dev/docs/features/circuit-breaker) for details.

## Examples

The [`examples/`](examples/src/main/java/dev/streamline/examples/) directory contains runnable examples:

| Example | Description |
|---------|-------------|
| [BasicUsage](examples/src/main/java/dev/streamline/examples/BasicUsage.java) | Produce, consume, and admin operations |
| [AdminClientUsage](examples/src/main/java/dev/streamline/examples/AdminClientUsage.java) | Topic, consumer group and cluster administration |
| [AgentMemoryUsage](examples/src/main/java/dev/streamline/examples/AgentMemoryUsage.java) | Agent memory remember/recall (experimental) |
| [QueryUsage](examples/src/main/java/dev/streamline/examples/QueryUsage.java) | SQL analytics with the embedded query engine |
| [SchemaRegistryUsage](examples/src/main/java/dev/streamline/examples/SchemaRegistryUsage.java) | Schema registration and validation |
| [CircuitBreakerUsage](examples/src/main/java/dev/streamline/examples/CircuitBreakerUsage.java) | Resilient production with circuit breaker |
| [SecurityUsage](examples/src/main/java/dev/streamline/examples/SecurityUsage.java) | TLS and SASL authentication |

Run any example with Maven:

```bash
mvn compile exec:java -pl examples -Dexec.mainClass=dev.streamline.examples.BasicUsage
```

Examples are compiled as part of every build, so they cannot drift from the API.

## Moonshot Features

> ⚠️ **Experimental** — These features require Streamline server 0.3.0+ with moonshot feature flags enabled.

### Semantic Search

Query topics by meaning instead of offset. Requires a topic created with `semantic.embed=true`.

```java
List<SearchHit> results = client.search("logs.app", "payment failure", 10);
for (SearchHit hit : results) {
    System.out.printf("[p%d] offset=%d score=%.2f%n",
        hit.getPartition(), hit.getOffset(), hit.getScore());
}
```

### Attestation Verification

Verify cryptographic provenance attestations attached to records by data contracts.

```java
import dev.streamline.client.StreamlineVerifier;

StreamlineVerifier verifier = new StreamlineVerifier(publicKeyBytes);
VerificationResult result = verifier.verify(record);
System.out.printf("Verified: %s, Producer: %s%n", result.isVerified(), result.getProducerId());
```

### Agent Memory (MCP)

Use Streamline as persistent memory for AI agents via the MCP protocol.

```java
import dev.streamline.client.MemoryClient;

MemoryClient memory = new MemoryClient("http://localhost:9094/mcp/v1");
memory.remember("user prefers dark mode", Map.of("tags", List.of("preferences")));
List<MemoryResult> results = memory.recall("user preferences", 5);
```

### Branched Streams

Create topic branches for replay, A/B testing, or counterfactual analysis.

```java
BranchInfo branch = admin.createBranch("events", "experiment-v2");
try (var consumer = client.consumer(branch.getTopic(), "branch-group")) {
    consumer.subscribe();
    var records = consumer.poll(Duration.ofMillis(100));
    // Process branched records independently
}
```

## Contributing

Contributions are welcome! Please see the [organization contributing guide](https://github.com/streamlinelabs/.github/blob/main/CONTRIBUTING.md) for guidelines.

## License

Apache 2.0
<!-- feat: 1fac2a38 -->

## Security

To report a security vulnerability, please email **security@streamline.dev**.
Do **not** open a public issue.

See the [Security Policy](https://github.com/streamlinelabs/streamline/blob/main/SECURITY.md) for details.

<!-- add Javadoc for public client interfaces -->



