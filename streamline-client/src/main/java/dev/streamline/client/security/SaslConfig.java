package dev.streamline.client.security;

import java.util.Objects;

/**
 * SASL authentication configuration.
 */
public class SaslConfig {

    private final SaslMechanism mechanism;
    private final String username;
    private final String password;

    private SaslConfig(Builder builder) {
        this.mechanism = Objects.requireNonNull(builder.mechanism, "mechanism must not be null");
        this.username = Objects.requireNonNull(builder.username, "username must not be null");
        this.password = Objects.requireNonNull(builder.password, "password must not be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public SaslMechanism getMechanism() {
        return mechanism;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Builder for {@link SaslConfig}.
     */
    public static class Builder {
        private SaslMechanism mechanism;
        private String username;
        private String password;

        private Builder() {}

        public Builder mechanism(SaslMechanism mechanism) {
            this.mechanism = mechanism;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public SaslConfig build() {
            return new SaslConfig(this);
        }
    }
}
