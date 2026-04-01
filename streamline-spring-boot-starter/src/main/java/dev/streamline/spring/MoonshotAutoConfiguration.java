package dev.streamline.spring;

import dev.streamline.client.moonshot.AttestationClient;
import dev.streamline.client.moonshot.BranchAdminClient;
import dev.streamline.client.moonshot.ContractsClient;
import dev.streamline.client.moonshot.MemoryClient;
import dev.streamline.client.moonshot.MoonshotClientOptions;
import dev.streamline.client.moonshot.SemanticSearchClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

/**
 * Auto-configuration for the Streamline moonshot HTTP clients
 * (M1 memory, M2 semantic search, M4 contracts + attestation, M5 branches).
 *
 * <p>Activated when the {@code dev.streamline.client.moonshot} package is on
 * the classpath and {@code streamline.moonshot.http-url} is set.
 */
@AutoConfiguration(after = StreamlineAutoConfiguration.class)
@ConditionalOnClass(BranchAdminClient.class)
@ConditionalOnProperty(prefix = "streamline.moonshot", name = "http-url")
@EnableConfigurationProperties(StreamlineProperties.class)
public class MoonshotAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MoonshotClientOptions moonshotClientOptions(StreamlineProperties properties) {
        StreamlineProperties.MoonshotProperties m = properties.getMoonshot();
        return new MoonshotClientOptions(m.getHttpUrl(),
            Duration.ofMillis(m.getTimeoutMs()), null);
    }

    @Bean
    @ConditionalOnMissingBean
    public BranchAdminClient branchAdminClient(MoonshotClientOptions opts) {
        return new BranchAdminClient(opts);
    }

    @Bean
    @ConditionalOnMissingBean
    public ContractsClient contractsClient(MoonshotClientOptions opts) {
        return new ContractsClient(opts);
    }

    @Bean
    @ConditionalOnMissingBean
    public AttestationClient attestationClient(MoonshotClientOptions opts, StreamlineProperties properties) {
        StreamlineProperties.MoonshotProperties m = properties.getMoonshot();
        return new AttestationClient(opts, m.getAttestationKeyId(), m.getAttestationAlgorithm());
    }

    @Bean
    @ConditionalOnMissingBean
    public SemanticSearchClient semanticSearchClient(MoonshotClientOptions opts) {
        return new SemanticSearchClient(opts);
    }

    @Bean
    @ConditionalOnMissingBean
    public MemoryClient memoryClient(MoonshotClientOptions opts) {
        return new MemoryClient(opts);
    }
}
