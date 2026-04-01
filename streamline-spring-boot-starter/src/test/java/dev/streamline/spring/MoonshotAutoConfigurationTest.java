package dev.streamline.spring;

import dev.streamline.client.moonshot.AttestationClient;
import dev.streamline.client.moonshot.BranchAdminClient;
import dev.streamline.client.moonshot.ContractsClient;
import dev.streamline.client.moonshot.MemoryClient;
import dev.streamline.client.moonshot.MoonshotClientOptions;
import dev.streamline.client.moonshot.SemanticSearchClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MoonshotAutoConfiguration}.
 */
class MoonshotAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MoonshotAutoConfiguration.class));

    @Test
    void noBeansRegisteredWithoutHttpUrl() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(BranchAdminClient.class);
            assertThat(context).doesNotHaveBean(ContractsClient.class);
            assertThat(context).doesNotHaveBean(AttestationClient.class);
            assertThat(context).doesNotHaveBean(SemanticSearchClient.class);
            assertThat(context).doesNotHaveBean(MemoryClient.class);
        });
    }

    @Test
    void allBeansRegisteredWhenHttpUrlSet() {
        contextRunner
            .withPropertyValues("streamline.moonshot.http-url=http://localhost:9094")
            .run(context -> {
                assertThat(context).hasSingleBean(MoonshotClientOptions.class);
                assertThat(context).hasSingleBean(BranchAdminClient.class);
                assertThat(context).hasSingleBean(ContractsClient.class);
                assertThat(context).hasSingleBean(AttestationClient.class);
                assertThat(context).hasSingleBean(SemanticSearchClient.class);
                assertThat(context).hasSingleBean(MemoryClient.class);

                MoonshotClientOptions opts = context.getBean(MoonshotClientOptions.class);
                assertThat(opts.httpUrl()).isEqualTo("http://localhost:9094");
                assertThat(opts.timeout().toMillis()).isEqualTo(10_000L);
            });
    }

    @Test
    void honorsCustomTimeoutAndAttestationProps() {
        contextRunner
            .withPropertyValues(
                "streamline.moonshot.http-url=http://broker:9094",
                "streamline.moonshot.timeout-ms=5000",
                "streamline.moonshot.attestation-key-id=my-key",
                "streamline.moonshot.attestation-algorithm=ed25519"
            )
            .run(context -> {
                MoonshotClientOptions opts = context.getBean(MoonshotClientOptions.class);
                assertThat(opts.timeout().toMillis()).isEqualTo(5000L);
                assertThat(context).hasSingleBean(AttestationClient.class);
            });
    }
}
