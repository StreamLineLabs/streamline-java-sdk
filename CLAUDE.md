# CLAUDE.md — Streamline Java SDK

## Overview
Java 17 SDK for [Streamline](https://github.com/streamlinelabs/streamline) with Spring Boot starter. Maven multi-module project. Communicates via the Kafka wire protocol on port 9092.

## Build & Test
```bash
mvn compile                # Build
mvn verify                 # Build + unit tests + package + SpotBugs (no server needed)
mvn test                   # Run unit tests only
mvn javadoc:javadoc        # Generate Javadoc

# Integration tests (*IT) — opt-in, needs a live server
docker compose -f docker-compose.test.yml up -d
STREAMLINE_INTEGRATION=1 mvn verify -Pintegration
```

## Architecture
```
streamline-java-sdk/
├── pom.xml                          # Parent POM with dependency management
├── streamline-client/               # Core client module
│   └── src/main/java/
│       └── com/streamlinelabs/client/
│           ├── StreamlineClient.java     # Main client
│           ├── StreamlineProducer.java   # Producer with batching
│           ├── StreamlineConsumer.java   # Consumer with groups
│           ├── StreamlineAdmin.java      # Admin operations
│           ├── config/                   # Configuration classes
│           ├── exception/                # Exception hierarchy
│           └── retry/                    # RetryPolicy
├── streamline-spring-boot-starter/  # Spring Boot auto-config
│   └── src/main/java/
│       └── com/streamlinelabs/spring/
│           ├── StreamlineAutoConfiguration.java
│           ├── StreamlineProperties.java
│           ├── StreamlineTemplate.java
│           └── @StreamlineListener annotation
├── examples/                        # Runnable examples, compiled but never published
└── testcontainers/                  # Standalone Testcontainers module (own coordinates)
```

## Coding Conventions
- **Builder pattern**: Use fluent builders for client/producer/consumer creation
- **Exception hierarchy**: `StreamlineException` base with `errorCode`, `retryable`, `hint`
- **Null safety**: Use `@Nullable`/`@NonNull` annotations, prefer `Optional` for return types
- **Resource management**: Implement `AutoCloseable`, use try-with-resources
- **Spring conventions**: Use `@ConditionalOnProperty` for conditional beans
- **Naming**: Standard Java — camelCase methods, PascalCase classes

## Spring Boot Integration
```java
@Service
public class EventService {
    @Autowired
    private StreamlineTemplate template;

    public void send(String event) {
        template.send("events", event);
    }
}

@Component
public class EventConsumer {
    @StreamlineListener(topics = "events")
    public void onEvent(ConsumerRecord<String, String> record) {
        // Handle event
    }
}
```

## Testing
- JUnit 5.10 + Mockito for unit tests; Testcontainers 1.19 for the container module
- **Unit tests (`*Test`, Surefire) must be hermetic** — never depend on a service running
  on the machine. Use `UnitTestEndpoints.BOOTSTRAP_SERVERS` (TEST-NET-1, unroutable) when
  a real client object is required; in-process stubs on ephemeral loopback ports are fine.
- **Integration tests (`*IT`, tagged `integration`, Failsafe)** need a live server. They
  are skipped unless `-Pintegration` is active, and require `STREAMLINE_INTEGRATION=1`;
  once enabled, an unreachable endpoint fails fast instead of skipping. Endpoints come
  from `dev.streamline.testsupport.IntegrationEnvironment`
  (`STREAMLINE_BOOTSTRAP_SERVERS`, `STREAMLINE_HTTP_URL`, `STREAMLINE_SCHEMA_REGISTRY_URL`).
- JaCoCo for coverage (runs on `verify`)
- SpotBugs for static analysis (runs on `verify`); exclusions live in `spotbugs-exclude.xml`

