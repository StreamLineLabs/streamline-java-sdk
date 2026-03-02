package dev.streamline.client.security;

/**
 * TLS/SSL configuration for secure connections.
 */
public class TlsConfig {

    private final String truststoreLocation;
    private final String truststorePassword;
    private final String keystoreLocation;
    private final String keystorePassword;
    private final String keyPassword;
    private final String endpointIdentificationAlgorithm;

    private TlsConfig(Builder builder) {
        this.truststoreLocation = builder.truststoreLocation;
        this.truststorePassword = builder.truststorePassword;
        this.keystoreLocation = builder.keystoreLocation;
        this.keystorePassword = builder.keystorePassword;
        this.keyPassword = builder.keyPassword;
        this.endpointIdentificationAlgorithm = builder.endpointIdentificationAlgorithm;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getTruststoreLocation() {
        return truststoreLocation;
    }

    public String getTruststorePassword() {
        return truststorePassword;
    }

    public String getKeystoreLocation() {
        return keystoreLocation;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public String getKeyPassword() {
        return keyPassword;
    }

    public String getEndpointIdentificationAlgorithm() {
        return endpointIdentificationAlgorithm;
    }

    /**
     * Builder for {@link TlsConfig}.
     */
    public static class Builder {
        private String truststoreLocation;
        private String truststorePassword;
        private String keystoreLocation;
        private String keystorePassword;
        private String keyPassword;
        private String endpointIdentificationAlgorithm = "https";

        private Builder() {}

        public Builder truststoreLocation(String truststoreLocation) {
            this.truststoreLocation = truststoreLocation;
            return this;
        }

        public Builder truststorePassword(String truststorePassword) {
            this.truststorePassword = truststorePassword;
            return this;
        }

        public Builder keystoreLocation(String keystoreLocation) {
            this.keystoreLocation = keystoreLocation;
            return this;
        }

        public Builder keystorePassword(String keystorePassword) {
            this.keystorePassword = keystorePassword;
            return this;
        }

        public Builder keyPassword(String keyPassword) {
            this.keyPassword = keyPassword;
            return this;
        }

        public Builder endpointIdentificationAlgorithm(String endpointIdentificationAlgorithm) {
            this.endpointIdentificationAlgorithm = endpointIdentificationAlgorithm;
            return this;
        }

        public TlsConfig build() {
            return new TlsConfig(this);
        }
    }
}
