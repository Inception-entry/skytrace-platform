package com.skytrace.backend.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytrace.backend.ai.dto.AiChatRequest;
import com.skytrace.backend.ai.dto.AiChatResponse;
import com.skytrace.backend.ai.dto.AiStreamResult;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Component
public class AiChatClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AiCallExecutor callExecutor;

    public AiChatClient(
            AiRestClientFactory restClientFactory,
            ObjectMapper objectMapper,
            AiCallExecutor callExecutor) {
        this.restClient = restClientFactory.create();
        this.objectMapper = objectMapper;
        this.callExecutor = callExecutor;
    }

    public String chat(String sessionId, String message) {
        return chat(sessionId, message, null);
    }

    public String chat(
            String sessionId,
            String message,
            String knowledgeQuery) {
        return chatResponse(sessionId, message, knowledgeQuery).answer();
    }

    public AiChatResponse chatResponse(
            String sessionId,
            String message,
            String knowledgeQuery) {
        String requestId = UUID.randomUUID().toString();
        AiChatResponse response = callExecutor.execute(
                "chat",
                requestId,
                sessionId,
                () -> restClient.post()
                        .uri("/api/chat")
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new AiChatRequest(
                                sessionId,
                                message,
                                knowledgeQuery
                        ))
                        .retrieve()
                        .body(AiChatResponse.class)
        );

        if (response == null || response.answer() == null) {
            throw new AiClientException(AiErrorCode.INVALID_RESPONSE);
        }

        return response;
    }

    public void stream(
            String sessionId,
            String message,
            String knowledgeQuery,
            OutputStream outputStream) {
        stream(
                sessionId,
                message,
                knowledgeQuery,
                outputStream,
                ignored -> {
                }
        );
    }

    public void stream(
            String sessionId,
            String message,
            String knowledgeQuery,
            OutputStream outputStream,
            Consumer<AiStreamResult> beforeDone) {
        String requestId = UUID.randomUUID().toString();
        AtomicBoolean eventForwarded = new AtomicBoolean(false);
        AtomicBoolean errorEventForwarded = new AtomicBoolean(false);
        try {
            callExecutor.execute(
                    "chat_stream",
                    requestId,
                    sessionId,
                    () -> {
                        consumeStream(
                                requestId,
                                sessionId,
                                message,
                                knowledgeQuery,
                                outputStream,
                                beforeDone,
                                eventForwarded,
                                errorEventForwarded
                        );
                        return null;
                    },
                    () -> !eventForwarded.get()
            );
        } catch (AiClientException exception) {
            if (errorEventForwarded.get()) {
                throw exception.withErrorEventForwarded();
            }
            throw exception;
        }
    }

    private void consumeStream(
            String requestId,
            String sessionId,
            String message,
            String knowledgeQuery,
            OutputStream outputStream,
            Consumer<AiStreamResult> beforeDone,
            AtomicBoolean eventForwarded,
            AtomicBoolean errorEventForwarded) {
        restClient.post()
                .uri("/api/chat/stream")
                .header("X-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(new AiChatRequest(sessionId, message, knowledgeQuery))
                .exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw AiClientExceptionClassifier.fromStatus(
                                response.getStatusCode().value()
                        );
                    }
                    AiSseEventAccumulator accumulator =
                            new AiSseEventAccumulator(objectMapper);
                    boolean terminalEvent = false;
                    try (var reader = new BufferedReader(
                            new InputStreamReader(
                                    response.getBody(),
                                    StandardCharsets.UTF_8
                            )
                    )) {
                        List<String> eventLines = new ArrayList<>();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.isEmpty()) {
                                if (!eventLines.isEmpty()) {
                                    terminalEvent = forwardEvent(
                                            eventLines,
                                            accumulator,
                                            outputStream,
                                            beforeDone,
                                            eventForwarded,
                                            errorEventForwarded
                                    );
                                    eventLines.clear();
                                    if (terminalEvent) {
                                        break;
                                    }
                                }
                            } else {
                                eventLines.add(line);
                            }
                        }
                        if (!terminalEvent && !eventLines.isEmpty()) {
                            terminalEvent = forwardEvent(
                                    eventLines,
                                    accumulator,
                                    outputStream,
                                    beforeDone,
                                    eventForwarded,
                                    errorEventForwarded
                            );
                        }
                    }
                    if (!terminalEvent) {
                        throw new AiClientException(
                                AiErrorCode.STREAM_INTERRUPTED
                        );
                    }
                    return null;
                });
    }

    private boolean forwardEvent(
            List<String> eventLines,
            AiSseEventAccumulator accumulator,
            OutputStream outputStream,
            Consumer<AiStreamResult> beforeDone,
            AtomicBoolean eventForwarded,
            AtomicBoolean errorEventForwarded) throws IOException {
        AiSseEventAccumulator.ParsedEvent event =
                accumulator.accept(eventLines);

        if (event.isDone()) {
            beforeDone.accept(event.completedResult());
        }

        String eventBlock = String.join("\n", eventLines) + "\n\n";
        // Once an SSE event is being sent, restarting the upstream stream can
        // duplicate metadata or tokens already observed by the browser.
        eventForwarded.set(true);
        outputStream.write(
                eventBlock.getBytes(StandardCharsets.UTF_8)
        );
        outputStream.flush();
        if (event.isError()) {
            errorEventForwarded.set(true);
            throw new AiClientException(
                    AiErrorCode.UPSTREAM_UNAVAILABLE
            );
        }

        return event.isDone();
    }
}
