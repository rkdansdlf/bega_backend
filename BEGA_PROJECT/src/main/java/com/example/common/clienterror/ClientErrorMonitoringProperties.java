package com.example.common.clienterror;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.client-error-monitoring")
public class ClientErrorMonitoringProperties {

    private int retentionDays = 30;
    private String cleanupCron = "0 15 3 * * *";
    private final Alerts alerts = new Alerts();

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public String getCleanupCron() {
        return cleanupCron;
    }

    public void setCleanupCron(String cleanupCron) {
        this.cleanupCron = cleanupCron;
    }

    public Alerts getAlerts() {
        return alerts;
    }

    public static class Alerts {
        private boolean enabled = false;
        private long pollIntervalMs = 60_000L;
        private int runtimeThreshold = 3;
        private int api5xxThreshold = 5;
        private int windowMinutes = 5;
        private int cooldownMinutes = 30;
        private String slackWebhookUrl = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        public int getRuntimeThreshold() {
            return runtimeThreshold;
        }

        public void setRuntimeThreshold(int runtimeThreshold) {
            this.runtimeThreshold = runtimeThreshold;
        }

        public int getApi5xxThreshold() {
            return api5xxThreshold;
        }

        public void setApi5xxThreshold(int api5xxThreshold) {
            this.api5xxThreshold = api5xxThreshold;
        }

        public int getWindowMinutes() {
            return windowMinutes;
        }

        public void setWindowMinutes(int windowMinutes) {
            this.windowMinutes = windowMinutes;
        }

        public int getCooldownMinutes() {
            return cooldownMinutes;
        }

        public void setCooldownMinutes(int cooldownMinutes) {
            this.cooldownMinutes = cooldownMinutes;
        }

        public String getSlackWebhookUrl() {
            return slackWebhookUrl;
        }

        public void setSlackWebhookUrl(String slackWebhookUrl) {
            this.slackWebhookUrl = slackWebhookUrl;
        }
    }
}
