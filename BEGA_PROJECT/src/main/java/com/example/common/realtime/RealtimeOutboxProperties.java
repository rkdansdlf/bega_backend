package com.example.common.realtime;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.realtime.outbox")
public class RealtimeOutboxProperties {

    private boolean enabled = true;
    private int batchSize = 100;
    private long pollIntervalMs = 200;
    private Duration leaseDuration = Duration.ofSeconds(30);
    private Duration retryBaseDelay = Duration.ofSeconds(1);
    private Duration retryMaxDelay = Duration.ofMinutes(5);
    private int maxAttempts = 100;
    private Duration retention = Duration.ofDays(7);
    private int cleanupBatchSize = 500;
    private int cleanupMaxBatches = 20;
    private Duration leaseClockSkew = Duration.ofSeconds(5);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public Duration getRetryBaseDelay() {
        return retryBaseDelay;
    }

    public void setRetryBaseDelay(Duration retryBaseDelay) {
        this.retryBaseDelay = retryBaseDelay;
    }

    public Duration getRetryMaxDelay() {
        return retryMaxDelay;
    }

    public void setRetryMaxDelay(Duration retryMaxDelay) {
        this.retryMaxDelay = retryMaxDelay;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getRetention() {
        return retention;
    }

    public void setRetention(Duration retention) {
        this.retention = retention;
    }

    public int getCleanupBatchSize() {
        return cleanupBatchSize;
    }

    public void setCleanupBatchSize(int cleanupBatchSize) {
        this.cleanupBatchSize = cleanupBatchSize;
    }

    public int getCleanupMaxBatches() {
        return cleanupMaxBatches;
    }

    public void setCleanupMaxBatches(int cleanupMaxBatches) {
        this.cleanupMaxBatches = cleanupMaxBatches;
    }

    public Duration getLeaseClockSkew() {
        return leaseClockSkew;
    }

    public void setLeaseClockSkew(Duration leaseClockSkew) {
        this.leaseClockSkew = leaseClockSkew;
    }
}
