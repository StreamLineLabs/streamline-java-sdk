package dev.streamline.spring;

import dev.streamline.client.Streamline;
import dev.streamline.client.schema.SchemaRegistryClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StreamlineAutoConfiguration}.
 */
class StreamlineAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(StreamlineAutoConfiguration.class));

    @Test
    void autoConfigurationRegistersStreamlineBean() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Streamline.class);
            assertThat(context).hasSingleBean(StreamlineTemplate.class);
        });
    }

    @Test
    void schemaRegistryClientNotRegisteredWithoutUrl() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(SchemaRegistryClient.class);
        });
    }

    @Test
    void schemaRegistryClientRegisteredWithUrl() {
        contextRunner
            .withPropertyValues("streamline.schema-registry-url=http://localhost:9094")
            .run(context -> {
                assertThat(context).hasSingleBean(SchemaRegistryClient.class);
            });
    }

    @Test
    void customBeansAreNotOverridden() {
        contextRunner
            .withBean(Streamline.class, () -> Streamline.builder()
                .bootstrapServers("custom:9092")
                .build())
            .run(context -> {
                assertThat(context).hasSingleBean(Streamline.class);
            });
    }

    @Test
    void propertiesAreApplied() {
        contextRunner
            .withPropertyValues(
                "streamline.bootstrap-servers=myhost:9092",
                "streamline.connection-pool-size=8",
                "streamline.connect-timeout-ms=5000"
            )
            .run(context -> {
                StreamlineProperties props = context.getBean(StreamlineProperties.class);
                assertThat(props.getBootstrapServers()).isEqualTo("myhost:9092");
                assertThat(props.getConnectionPoolSize()).isEqualTo(8);
                assertThat(props.getConnectTimeoutMs()).isEqualTo(5000);
            });
    }

    @Test
    void defaultPropertyValues() {
        contextRunner.run(context -> {
            StreamlineProperties props = context.getBean(StreamlineProperties.class);
            assertThat(props.getBootstrapServers()).isEqualTo("localhost:9092");
            assertThat(props.getConnectionPoolSize()).isEqualTo(4);
            assertThat(props.getConnectTimeoutMs()).isEqualTo(30000);
            assertThat(props.getRequestTimeoutMs()).isEqualTo(30000);
            assertThat(props.getSchemaRegistryUrl()).isNull();
        });
    }
}
