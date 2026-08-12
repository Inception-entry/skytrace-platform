package com.skytrace.backend.device.mqtt;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * MQTT 生命周期：连接 Broker、订阅设备 Topic、停机时断开。
 * 业务判断全部交给 {@link DeviceMqttMessageHandler}。
 */
@Component
@ConditionalOnProperty(name = "app.mqtt.enabled", havingValue = "true")
public class DeviceMqttSubscriber {

    private static final Logger log = LoggerFactory.getLogger(DeviceMqttSubscriber.class);
    private static final int CONNECT_MAX_ATTEMPTS = 30;
    private static final long CONNECT_RETRY_MS = 1000L;

    private final MqttProperties properties;
    private final DeviceMqttMessageHandler handler;

    private volatile MqttClient client;

    public DeviceMqttSubscriber(
            MqttProperties properties,
            DeviceMqttMessageHandler handler) {
        this.properties = properties;
        this.handler = handler;
    }

    @PostConstruct
    public void start() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);

        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            options.setUserName(properties.getUsername());
            options.setPassword(properties.getPassword().toCharArray());
        }

        String clientId = properties.getClientId() + "-" + UUID.randomUUID();
        final MqttClient mqttClient;
        try {
            mqttClient = new MqttClient(
                    properties.getBrokerUrl(),
                    clientId,
                    new MemoryPersistence()
            );
        } catch (MqttException error) {
            log.warn(
                    "MQTT 客户端创建失败: broker={} reason={}",
                    properties.getBrokerUrl(),
                    error.getMessage()
            );
            return;
        }
        this.client = mqttClient;

        mqttClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverUri) {
                try {
                    subscribeAll(mqttClient);
                    if (reconnect) {
                        log.info("MQTT 自动重连成功并已重新订阅: {}", serverUri);
                    }
                } catch (MqttException error) {
                    log.warn("MQTT 重连后订阅失败: {}", error.getMessage());
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                log.warn("MQTT 连接丢失: {}", cause == null ? "unknown" : cause.getMessage());
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                // 使用带 IMqttMessageListener 的 subscribe，消息不会走到这里。
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // 本服务只订阅，不发布。
            }
        });

        // 异步连接：避免 broker 未就绪时阻塞 Spring / 拖垮 CI readiness
        Thread connector = new Thread(
                () -> {
                    try {
                        connectWithRetry(mqttClient, options);
                        log.info("MQTT 已连接: broker={}, clientId={}",
                                properties.getBrokerUrl(), clientId);
                    } catch (Exception error) {
                        log.warn(
                                "MQTT 初始连接失败，将依赖后续重试/重建: broker={} reason={}",
                                properties.getBrokerUrl(),
                                error.getMessage()
                        );
                    }
                },
                "mqtt-connect"
        );
        connector.setDaemon(true);
        connector.start();
    }

    @PreDestroy
    public void stop() {
        MqttClient mqttClient = this.client;
        if (mqttClient == null) {
            return;
        }
        try {
            if (mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
        } catch (Exception error) {
            log.debug("MQTT disconnect 忽略: {}", error.getMessage());
        }
        try {
            mqttClient.close();
        } catch (Exception error) {
            log.debug("MQTT close 忽略: {}", error.getMessage());
        }
        this.client = null;
        log.info("MQTT 订阅已关闭");
    }

    private void connectWithRetry(MqttClient mqttClient, MqttConnectOptions options)
            throws Exception {
        MqttException last = null;
        for (int attempt = 1; attempt <= CONNECT_MAX_ATTEMPTS; attempt++) {
            try {
                mqttClient.connect(options);
                return;
            } catch (MqttException error) {
                last = error;
                log.warn("MQTT 连接失败 ({}/{}): {}",
                        attempt, CONNECT_MAX_ATTEMPTS, error.getMessage());
                if (attempt == CONNECT_MAX_ATTEMPTS) {
                    break;
                }
                Thread.sleep(CONNECT_RETRY_MS);
            }
        }
        throw last == null
                ? new IllegalStateException("MQTT 连接失败")
                : last;
    }

    private void subscribeAll(MqttClient mqttClient) throws MqttException {
        String prefix = "skytrace/" + properties.getEnv() + "/device/+/";
        String heartbeatFilter = prefix + "heartbeat";
        String statusFilter = prefix + "status";
        String telemetryFilter = prefix + "telemetry";

        mqttClient.subscribe(heartbeatFilter, 0, (topic, message) ->
                handler.onMessage(
                        topic,
                        new String(message.getPayload(), StandardCharsets.UTF_8)
                )
        );
        mqttClient.subscribe(statusFilter, 1, (topic, message) ->
                handler.onMessage(
                        topic,
                        new String(message.getPayload(), StandardCharsets.UTF_8)
                )
        );
        mqttClient.subscribe(telemetryFilter, 0, (topic, message) ->
                handler.onMessage(
                        topic,
                        new String(message.getPayload(), StandardCharsets.UTF_8)
                )
        );

        log.info("MQTT 已订阅: {} , {} , {}",
                heartbeatFilter, statusFilter, telemetryFilter);
    }
}
