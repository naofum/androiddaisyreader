package com.github.library.dodp.client;

import java.net.URI;
import java.time.Duration;

/**
 * Immutable configuration for a {@link DodpClient}: service endpoint, timeouts,
 * retry behaviour and transport settings.
 */
public final class DodpConfiguration {

    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(20);
    public static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(60);
    public static final int DEFAULT_MAX_RETRIES = 2;
    public static final Duration DEFAULT_RETRY_BACKOFF = Duration.ofMillis(500);

    private final URI endpoint;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final int maxRetries;
    private final Duration retryBackoff;
    private final String userAgent;
    private final boolean autoRelogin;

    private DodpConfiguration(URI endpoint, Duration connectTimeout, Duration readTimeout,
                              int maxRetries, Duration retryBackoff, String userAgent, boolean autoRelogin) {
        this.endpoint = endpoint;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.maxRetries = maxRetries;
        this.retryBackoff = retryBackoff;
        this.userAgent = userAgent;
        this.autoRelogin = autoRelogin;
    }

    public URI getEndpoint() {
        return endpoint;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public Duration getRetryBackoff() {
        return retryBackoff;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public boolean isAutoRelogin() {
        return autoRelogin;
    }

    public static Builder builder(String endpoint) {
        return new Builder(URI.create(endpoint));
    }

    public static Builder builder(URI endpoint) {
        return new Builder(endpoint);
    }

    public static final class Builder {

        private final URI endpoint;
        private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private Duration readTimeout = DEFAULT_READ_TIMEOUT;
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private Duration retryBackoff = DEFAULT_RETRY_BACKOFF;
        private String userAgent = "dodp-client/1.0";
        private boolean autoRelogin = true;

        private Builder(URI endpoint) {
            if (endpoint == null) {
                throw new IllegalArgumentException("endpoint must not be null");
            }
            this.endpoint = endpoint;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries must be >= 0");
            }
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder retryBackoff(Duration retryBackoff) {
            this.retryBackoff = retryBackoff;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder autoRelogin(boolean autoRelogin) {
            this.autoRelogin = autoRelogin;
            return this;
        }

        public DodpConfiguration build() {
            return new DodpConfiguration(endpoint, connectTimeout, readTimeout, maxRetries,
                    retryBackoff, userAgent, autoRelogin);
        }
    }
}
