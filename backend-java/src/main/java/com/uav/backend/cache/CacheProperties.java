package com.uav.backend.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cache")
public class CacheProperties {
    private boolean enabled = true;
    private long alarmTtlSeconds = 30;
    private long devicePresenceTtlSeconds = 90;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getAlarmTtlSeconds() {
        return alarmTtlSeconds;
    }

    public void setAlarmTtlSeconds(long alarmTtlSeconds) {
        this.alarmTtlSeconds = alarmTtlSeconds;
    }

    public long getDevicePresenceTtlSeconds() {
        return devicePresenceTtlSeconds;
    }

    public void setDevicePresenceTtlSeconds(long devicePresenceTtlSeconds) {
        this.devicePresenceTtlSeconds = devicePresenceTtlSeconds;
    }
}
