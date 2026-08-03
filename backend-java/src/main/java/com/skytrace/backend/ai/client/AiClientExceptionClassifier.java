package com.skytrace.backend.ai.client;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;

final class AiClientExceptionClassifier {

    private AiClientExceptionClassifier() {
    }

    static AiClientException classify(Throwable throwable) {
        if (throwable instanceof AiClientException exception) {
            return exception;
        }

        if (throwable instanceof RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 408 || status == 504) {
                return new AiClientException(AiErrorCode.TIMEOUT, exception);
            }
            if (status == 429) {
                return new AiClientException(
                        AiErrorCode.RATE_LIMITED,
                        exception
                );
            }
            if (status >= 500) {
                return new AiClientException(
                        AiErrorCode.UPSTREAM_UNAVAILABLE,
                        exception
                );
            }
            return new AiClientException(
                    AiErrorCode.REQUEST_REJECTED,
                    exception
            );
        }

        if (hasCause(
                throwable,
                SocketTimeoutException.class,
                HttpTimeoutException.class,
                HttpConnectTimeoutException.class
        )) {
            return new AiClientException(AiErrorCode.TIMEOUT, throwable);
        }

        if (throwable instanceof ResourceAccessException
                || hasCause(throwable, ConnectException.class)) {
            return new AiClientException(
                    AiErrorCode.CONNECTION_FAILED,
                    throwable
            );
        }

        if (throwable instanceof IOException) {
            return new AiClientException(
                    AiErrorCode.STREAM_INTERRUPTED,
                    throwable
            );
        }

        return new AiClientException(
                AiErrorCode.UPSTREAM_UNAVAILABLE,
                throwable
        );
    }

    static AiClientException fromStatus(int status) {
        if (status == 408 || status == 504) {
            return new AiClientException(AiErrorCode.TIMEOUT);
        }
        if (status == 429) {
            return new AiClientException(AiErrorCode.RATE_LIMITED);
        }
        if (status >= 500) {
            return new AiClientException(AiErrorCode.UPSTREAM_UNAVAILABLE);
        }
        return new AiClientException(AiErrorCode.REQUEST_REJECTED);
    }

    @SafeVarargs
    private static boolean hasCause(
            Throwable throwable,
            Class<? extends Throwable>... types) {
        Throwable current = throwable;
        while (current != null) {
            for (Class<? extends Throwable> type : types) {
                if (type.isInstance(current)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
