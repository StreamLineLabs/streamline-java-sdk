# Testcontainers Streamline (Java)

[![Maven Central](https://img.shields.io/maven-central/v/io.streamline/testcontainers-streamline?style=flat-square)](https://search.maven.org/artifact/io.streamline/testcontainers-streamline)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue?style=flat-square)](LICENSE)

Testcontainers module for [Streamline](https://github.com/streamlinelabs/streamline) — **5x faster** than Kafka containers (~1s vs ~15s startup).

## Features

- Kafka-compatible container for testing
- Fast startup (~100ms vs seconds for Kafka)
- Low memory footprint (<50MB)
- No ZooKeeper or KRaft required
- Built-in health checks

## Installation

### Maven

```xml
<dependency>
    <groupId>io.streamline</groupId>
    <artifactId>testcontainers-streamline</artifactId>
    <version>0.2.0</version>
    <scope>test</scope>
</dependency>
```

### Gradle

```groovy
testImplementation 'io.streamline:testcontainers-streamline:0.2.0'
```

## Usage

### Basic Usage

```java
@Testcontainers
class MyKafkaTest {

    @Container
    static StreamlineContainer streamline = new StreamlineContainer();

    @Test
    void testWithKafka() {
        String bootstrapServers = streamline.getBootstrapServers();

        // Use with any Kafka client
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", StringSerializer.class.getName());

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>("my-topic", "key", "value")).get();
        }
    }
}
```

### With Debug Logging

```java
@Container
static StreamlineContainer streamline = new StreamlineContainer()
        .withDebugLogging();
```

### Create Topics Programmatically

```java
@BeforeAll
static void setup() {
    streamline.createTopic("my-topic", 3); // 3 partitions
}
```

### Access HTTP API

```java
@Test
void testHealthCheck() {
    String healthUrl = streamline.getHealthUrl();
    String metricsUrl = streamline.getMetricsUrl();
    // Use with HTTP client
}
```

### Specific Version

```java
@Container
static StreamlineContainer streamline = new StreamlineContainer("0.2.0");
```

## API Reference

### StreamlineContainer

| Method | Description |
|--------|-------------|
| `getBootstrapServers()` | Returns Kafka bootstrap servers string |
| `getHttpUrl()` | Returns HTTP API base URL |
| `getHealthUrl()` | Returns health check endpoint URL |
| `getMetricsUrl()` | Returns Prometheus metrics URL |
| `withLogLevel(String)` | Sets log level (trace, debug, info, warn, error) |
| `withDebugLogging()` | Enables debug logging |
| `createTopic(String, int)` | Creates a topic with specified partitions |

## Example with Spring Kafka

```java
@SpringBootTest
@Testcontainers
class SpringKafkaTest {

    @Container
    static StreamlineContainer streamline = new StreamlineContainer();

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", streamline::getBootstrapServers);
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void testSendMessage() {
        kafkaTemplate.send("my-topic", "hello").get();
    }
}
```

## License

Apache-2.0
