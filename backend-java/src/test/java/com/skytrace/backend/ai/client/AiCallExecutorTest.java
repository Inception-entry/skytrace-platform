package com.skytrace.backend.ai.client;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiCallExecutorTest {

    @Test
    void retriesTransientFailureAndUsesConfiguredBackoff() {
        AiClientProperties properties = properties();
        List<Duration> sleeps = new ArrayList<>();
        AiCallExecutor executor = new AiCallExecutor(
                properties,
                sleeps::add
        );
        AtomicInteger calls = new AtomicInteger();

        String result = executor.execute(
                "chat",
                "request-1",
                "session-1",
                () -> {
                    if (calls.incrementAndGet() == 1) {
                        throw new AiClientException(
                                AiErrorCode.CONNECTION_FAILED
                        );
                    }
                    return "ok";
                }
        );

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(2);
        assertThat(sleeps).containsExactly(Duration.ofMillis(10));
    }

    @Test
    void doesNotImmediatelyRetryLongRunningTimeout() {
        AiClientProperties properties = properties();
        AtomicInteger calls = new AtomicInteger();
        AiCallExecutor executor = new AiCallExecutor(
                properties,
                ignored -> {
                }
        );

        assertThatThrownBy(() -> executor.execute(
                "chat",
                "request-2",
                "session-2",
                () -> {
                    calls.incrementAndGet();
                    throw new AiClientException(AiErrorCode.TIMEOUT);
                }
        ))
                .isInstanceOf(AiClientException.class)
                .extracting("errorCode")
                .isEqualTo(AiErrorCode.TIMEOUT);
        assertThat(calls).hasValue(1);
    }

    @Test
    void doesNotRetryStreamAfterAnEventWasForwarded() {
        AiClientProperties properties = properties();
        AtomicInteger calls = new AtomicInteger();
        AiCallExecutor executor = new AiCallExecutor(
                properties,
                ignored -> {
                }
        );

        assertThatThrownBy(() -> executor.execute(
                "chat_stream",
                "request-3",
                "session-3",
                () -> {
                    calls.incrementAndGet();
                    throw new AiClientException(
                            AiErrorCode.STREAM_INTERRUPTED
                    );
                },
                () -> false
        )).isInstanceOf(AiClientException.class);
        assertThat(calls).hasValue(1);
    }

    private AiClientProperties properties() {
        AiClientProperties properties = new AiClientProperties();
        properties.setMaxAttempts(2);
        properties.setInitialBackoff(Duration.ofMillis(10));
        properties.setMaxBackoff(Duration.ofMillis(20));
        return properties;
    }
}
