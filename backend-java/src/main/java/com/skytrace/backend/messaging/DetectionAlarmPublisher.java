package com.skytrace.backend.messaging;

import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(
        name = "app.messaging.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DetectionAlarmPublisher {

    static final long CONFIRM_TIMEOUT_SECONDS = 5;

    private final RabbitTemplate rabbitTemplate;

    public DetectionAlarmPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(DetectionAlarmMessage message) {
        CorrelationData correlation = new CorrelationData();
        rabbitTemplate.convertAndSend(
                MessagingProperties.DETECTION_EXCHANGE,
                MessagingProperties.DETECTION_ROUTING_KEY,
                message,
                correlation
        );
        CorrelationData.Confirm confirm;
        try {
            confirm = correlation.getFuture().get(
                    CONFIRM_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (TimeoutException ex) {
            throw new BrokerPublishException("RabbitMQ 确认超时", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BrokerPublishException("等待 RabbitMQ 确认被中断", ex);
        } catch (ExecutionException ex) {
            throw new BrokerPublishException("等待 RabbitMQ 确认失败", ex);
        }
        if (confirm == null || !confirm.isAck()) {
            String reason = confirm == null ? "empty" : confirm.getReason();
            throw new BrokerPublishException(
                    "RabbitMQ 拒绝 detection 告警: " + reason
            );
        }
        if (correlation.getReturned() != null) {
            throw new BrokerPublishException("detection 告警无法路由到队列");
        }
    }
}
