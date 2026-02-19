# Streamline Java SDK

[![CI](https://github.com/streamlinelabs/streamline-java-sdk/actions/workflows/ci.yml/badge.svg)](https://github.com/streamlinelabs/streamline-java-sdk/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://openjdk.org/)
[![Docs](https://img.shields.io/badge/docs-streamlinelabs.dev-blue.svg)](https://streamlinelabs.dev/docs/sdks/java)

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
    <version>0.2.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'dev.streamline:streamline-client:0.2.0'
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
    <version>0.2.0</version>
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
- **Connection Pooling**: Automatic connection management
- **Automatic Reconnection**: Handles transient failures
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
    <version>0.2.0</version>
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
- Streamline server 0.2.0 or later

## Building from Source

```bash
cd sdks/java
./mvnw clean install
```

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

## License

Apache 2.0
