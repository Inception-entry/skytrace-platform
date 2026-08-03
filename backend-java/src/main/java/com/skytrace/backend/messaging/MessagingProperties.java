package com.skytrace.backend.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.messaging")
public class MessagingProperties {

    public static final String DETECTION_EXCHANGE = "skytrace.detection";
    public static final String DETECTION_QUEUE = "skytrace.detection.alarms";
    public static final String DETECTION_ROUTING_KEY = "alarm";
    public static final String REALTIME_EXCHANGE = "skytrace.alarm.realtime";
    public static final String REALTIME_QUEUE = "skytrace.alarm.realtime.node";

    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
