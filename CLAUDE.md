# CLAUDE.md — Streamline Java SDK

## Overview
Java 17 SDK for [Streamline](https://github.com/streamlinelabs/streamline) with Spring Boot starter. Maven multi-module project. Communicates via the Kafka wire protocol on port 9092.

## Build & Test
```bash
mvn compile                # Build
mvn verify                 # Build + test + SpotBugs
mvn test                   # Run tests only
mvn javadoc:javadoc        # Generate Javadoc
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
- JUnit 5.10 + Mockito 5.7 for unit tests
- Testcontainers 1.19 for integration tests
- JaCoCo for coverage (runs on `verify`)
- SpotBugs for static analysis (runs on `verify`)

