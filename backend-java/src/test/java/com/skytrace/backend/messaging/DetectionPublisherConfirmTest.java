package com.skytrace.backend.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DetectionPublisherConfirmTest {

    @Test
    void publishWaitsForBrokerAck() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).convertAndSend(
                eq("skytrace.detection"),
                eq("alarm"),
                any(DetectionAlarmMessage.class),
                any(CorrelationData.class)
        );

        new DetectionAlarmPublisher(template).publish(sampleMessage());

        verify(template).convertAndSend(
                eq("skytrace.detection"),
                eq("alarm"),
                any(DetectionAlarmMessage.class),
                any(CorrelationData.class)
        );
    }

    @Test
    void publishFailsWhenBrokerNacks() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(
                    new CorrelationData.Confirm(false, "nack")
            );
            return null;
        }).when(template).convertAndSend(
                eq("skytrace.detection"),
                eq("alarm"),
                any(DetectionAlarmMessage.class),
                any(CorrelationData.class)
        );

        assertThatThrownBy(() -> new DetectionAlarmPublisher(template).publish(sampleMessage()))
                .isInstanceOf(BrokerPublishException.class)
                .hasMessageContaining("拒绝");
    }

    @Test
    void publishFailsWhenConfirmTimesOut() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().completeExceptionally(
                    new TimeoutException("confirm timeout")
            );
            return null;
        }).when(template).convertAndSend(
                eq("skytrace.detection"),
                eq("alarm"),
                any(DetectionAlarmMessage.class),
                any(CorrelationData.class)
        );

        assertThatThrownBy(
                () -> new DetectionAlarmPublisher(template).publish(sampleMessage())
        ).isInstanceOf(BrokerPublishException.class);
    }

    @Test
    void yamlEnablesCorrelatedConfirmsAndReturns() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties props = yaml.getObject();
        assertThat(props).isNotNull();
        assertThat(props.getProperty("spring.rabbitmq.publisher-confirm-type"))
                .isEqualTo("correlated");
        assertThat(props.getProperty("spring.rabbitmq.publisher-returns"))
                .isEqualTo("true");
        assertThat(props.getProperty("spring.rabbitmq.template.mandatory"))
                .isEqualTo("true");
    }

    private static DetectionAlarmMessage sampleMessage() {
        return new DetectionAlarmMessage(
                "UAV-1",
                "TASK-1",
                "WEAPON_DETECTED",
                "KNIFE",
                BigDecimal.ONE,
                null,
                null,
                null,
                null,
                LocalDateTime.of(2030, 1, 1, 8, 0),
                "550e8400-e29b-41d4-a716-446655440000"
        );
    }
}
