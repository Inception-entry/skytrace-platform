package com.skytrace.backend.ai.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class AiClientExceptionClassifierTest {

    @Test
    void classifiesGatewayTimeoutAsTimeout() {
        AiClientException exception = AiClientExceptionClassifier.classify(
                new HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT)
        );

        assertThat(exception.errorCode()).isEqualTo(AiErrorCode.TIMEOUT);
        assertThat(exception.retryable()).isTrue();
    }

    @Test
    void classifiesNestedSocketTimeoutAsTimeout() {
        AiClientException exception = AiClientExceptionClassifier.classify(
                new ResourceAccessException(
                        "read timed out",
                        new SocketTimeoutException()
                )
        );

        assertThat(exception.errorCode()).isEqualTo(AiErrorCode.TIMEOUT);
    }

    @Test
    void classifiesClientStatusAsNonRetryableRejection() {
        AiClientException exception =
                AiClientExceptionClassifier.fromStatus(400);

        assertThat(exception.errorCode())
                .isEqualTo(AiErrorCode.REQUEST_REJECTED);
        assertThat(exception.retryable()).isFalse();
    }
}
