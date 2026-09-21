package com.skytrace.backend.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class DetectionDeadLetterConfigTest {

    @Test
    void detectionQueuePointsAtDlx() {
        RabbitMqConfig config = new RabbitMqConfig();
        Queue queue = config.detectionQueue();
        Queue dlq = config.detectionDlq();
        Binding binding = config.detectionDlqBinding(dlq, config.detectionDlx());

        assertThat(queue.getName()).isEqualTo("skytrace.detection.alarms");
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", "skytrace.detection.dlx")
                .containsEntry("x-dead-letter-routing-key", "alarm.dlq");
        assertThat(dlq.getName()).isEqualTo("skytrace.detection.alarms.dlq");
        assertThat(binding.getExchange()).isEqualTo("skytrace.detection.dlx");
        assertThat(binding.getRoutingKey()).isEqualTo("alarm.dlq");
    }

    @Test
    void listenerDoesNotRequeueAfterRetryBudget() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties props = yaml.getObject();
        assertThat(props).isNotNull();
        assertThat(props.getProperty(
                "spring.rabbitmq.listener.simple.default-requeue-rejected"
        )).isEqualTo("false");
        assertThat(props.getProperty(
                "spring.rabbitmq.listener.simple.retry.enabled"
        )).isEqualTo("true");
        assertThat(props.getProperty(
                "spring.rabbitmq.listener.simple.retry.max-attempts"
        )).isEqualTo("${RABBIT_LISTENER_MAX_ATTEMPTS:3}");
    }
}
