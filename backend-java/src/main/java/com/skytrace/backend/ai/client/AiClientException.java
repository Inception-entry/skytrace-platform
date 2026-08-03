package com.skytrace.backend.ai.client;

public class AiClientException extends RuntimeException {

    private final AiErrorCode errorCode;
    private final boolean errorEventForwarded;

    public AiClientException(AiErrorCode errorCode) {
        this(errorCode, null, false);
    }

    public AiClientException(
            AiErrorCode errorCode,
            Throwable cause) {
        this(errorCode, cause, false);
    }

    public AiClientException(
            AiErrorCode errorCode,
            Throwable cause,
            boolean errorEventForwarded) {
        super(errorCode.safeMessage(), cause);
        this.errorCode = errorCode;
        this.errorEventForwarded = errorEventForwarded;
    }

    public AiErrorCode errorCode() {
        return errorCode;
    }

    public boolean retryable() {
        return errorCode.retryable();
    }

    public boolean errorEventForwarded() {
        return errorEventForwarded;
    }

    public AiClientException withErrorEventForwarded() {
        if (errorEventForwarded) {
            return this;
        }
        return new AiClientException(errorCode, this, true);
    }
}
