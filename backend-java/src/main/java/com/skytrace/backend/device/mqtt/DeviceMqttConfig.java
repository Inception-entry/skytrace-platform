package com.skytrace.backend.device.mqtt;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MqttProperties.class)
@ConditionalOnProperty(
        name = "app.mqtt.enabled",
        havingValue = "true"
)
public class DeviceMqttConfig {
}
