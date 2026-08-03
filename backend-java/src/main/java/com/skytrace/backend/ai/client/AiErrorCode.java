package com.skytrace.backend.ai.client;

import org.springframework.http.HttpStatus;

public enum AiErrorCode {
    TIMEOUT(
            "AI 服务响应超时，请稍后重试",
            HttpStatus.GATEWAY_TIMEOUT,
            true
    ),
    CONNECTION_FAILED(
            "暂时无法连接 AI 服务，请稍后重试",
            HttpStatus.SERVICE_UNAVAILABLE,
            true
    ),
    RATE_LIMITED(
            "AI 服务请求过于频繁，请稍后重试",
            HttpStatus.TOO_MANY_REQUESTS,
            true
    ),
    UPSTREAM_UNAVAILABLE(
            "AI 服务暂时不可用，请稍后重试",
            HttpStatus.BAD_GATEWAY,
            true
    ),
    REQUEST_REJECTED(
            "AI 服务拒绝了本次请求",
            HttpStatus.BAD_GATEWAY,
            false
    ),
    INVALID_RESPONSE(
            "AI 服务返回了无效结果",
            HttpStatus.BAD_GATEWAY,
            false
    ),
    STREAM_INTERRUPTED(
            "AI 流式响应意外中断，请重试",
            HttpStatus.BAD_GATEWAY,
            true
    ),
    CLIENT_INTERRUPTED(
            "AI 请求已被中断",
            HttpStatus.SERVICE_UNAVAILABLE,
            false
    );

    private final String safeMessage;
    private final HttpStatus httpStatus;
    private final boolean retryable;

    AiErrorCode(
            String safeMessage,
            HttpStatus httpStatus,
            boolean retryable) {
        this.safeMessage = safeMessage;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public String safeMessage() {
        return safeMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public boolean retryable() {
        return retryable;
    }
}
