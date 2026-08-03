package com.skytrace.backend.ai.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@Component
public class AiCallExecutor {

    private static final Logger log = LoggerFactory.getLogger(
            AiCallExecutor.class
    );

    private final AiClientProperties properties;
    private final Sleeper sleeper;

    @Autowired
    public AiCallExecutor(AiClientProperties properties) {
        this(properties, duration -> Thread.sleep(duration.toMillis()));
    }

    AiCallExecutor(
            AiClientProperties properties,
            Sleeper sleeper) {
        this.properties = properties;
        this.sleeper = sleeper;
    }

    public <T> T execute(
            String operation,
            String requestId,
            String sessionId,
            Supplier<T> call) {
        return execute(
                operation,
                requestId,
                sessionId,
                call,
                () -> true
        );
    }

    public <T> T execute(
            String operation,
            String requestId,
            String sessionId,
            Supplier<T> call,
            BooleanSupplier retryAllowed) {
        Duration backoff = properties.getInitialBackoff();
        int maxAttempts = properties.getMaxAttempts();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long startedAt = System.nanoTime();
            try {
                T result = call.get();
                log.info(
                        "event=ai_call_success operation={} requestId={}"
                                + " sessionId={} attempt={} durationMs={}",
                        operation,
                        requestId,
                        safeSessionId(sessionId),
                        attempt,
                        elapsedMillis(startedAt)
                );
                return result;
            } catch (RuntimeException exception) {
                AiClientException classified =
                        AiClientExceptionClassifier.classify(exception);
                boolean retry = shouldRetryImmediately(classified)
                        && retryAllowed.getAsBoolean()
                        && attempt < maxAttempts;

                if (!retry) {
                    log.error(
                            "event=ai_call_failed operation={} requestId={}"
                                    + " sessionId={} attempt={} durationMs={}"
                                    + " errorCode={} retryable={}"
                                    + " exceptionType={}",
                            operation,
                            requestId,
                            safeSessionId(sessionId),
                            attempt,
                            elapsedMillis(startedAt),
                            classified.errorCode(),
                            classified.retryable(),
                            exception.getClass().getSimpleName()
                    );
                    throw classified;
                }

                log.warn(
                        "event=ai_call_retry operation={} requestId={}"
                                + " sessionId={} attempt={} nextAttempt={}"
                                + " durationMs={} backoffMs={} errorCode={}",
                        operation,
                        requestId,
                        safeSessionId(sessionId),
                        attempt,
                        attempt + 1,
                        elapsedMillis(startedAt),
                        backoff.toMillis(),
                        classified.errorCode()
                );
                sleep(backoff, operation, requestId, sessionId);
                backoff = nextBackoff(backoff);
            }
        }

        throw new IllegalStateException("Unreachable retry state");
    }

    private void sleep(
            Duration duration,
            String operation,
            String requestId,
            String sessionId) {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn(
                    "event=ai_call_interrupted operation={} requestId={}"
                            + " sessionId={}",
                    operation,
                    requestId,
                    safeSessionId(sessionId)
            );
            throw new AiClientException(
                    AiErrorCode.CLIENT_INTERRUPTED,
                    exception
            );
        }
    }

    private Duration nextBackoff(Duration current) {
        Duration doubled = current.multipliedBy(2);
        return doubled.compareTo(properties.getMaxBackoff()) > 0
                ? properties.getMaxBackoff()
                : doubled;
    }

    private boolean shouldRetryImmediately(
            AiClientException exception) {
        // The FastAPI layer already retries a timed-out model invocation.
        // Retrying that long-running timeout again here would multiply the
        // total request duration. Temporal remains the durable retry layer.
        return exception.retryable()
                && exception.errorCode() != AiErrorCode.TIMEOUT;
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String safeSessionId(String sessionId) {
        return sessionId == null || sessionId.isBlank()
                ? "-"
                : sessionId.replaceAll("[^a-zA-Z0-9._:-]", "_");
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }
}
