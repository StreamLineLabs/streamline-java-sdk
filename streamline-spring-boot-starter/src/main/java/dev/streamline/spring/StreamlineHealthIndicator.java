package dev.streamline.spring;

import dev.streamline.client.Streamline;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

/**
 * Spring Boot Actuator health indicator for Streamline connectivity.
 * Reports UP when the client can reach the broker, DOWN otherwise.
 * Automatically registered when spring-boot-starter-actuator is on the classpath.
 */
public class StreamlineHealthIndicator extends AbstractHealthIndicator {

    private final Streamline streamline;

    public StreamlineHealthIndicator(Streamline streamline) {
        super("Streamline health check failed");
        this.streamline = streamline;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        if (streamline.isHealthy()) {
            builder.up()
                .withDetail("bootstrapServers", streamline.getConfig().getBootstrapServers())
                .withDetail("status", "connected");
        } else {
            builder.down()
                .withDetail("bootstrapServers", streamline.getConfig().getBootstrapServers())
                .withDetail("status", "disconnected");
        }
    }
}
