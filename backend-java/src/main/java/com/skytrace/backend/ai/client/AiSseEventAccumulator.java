package com.skytrace.backend.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytrace.backend.ai.dto.AiKnowledgeSource;
import com.skytrace.backend.ai.dto.AiStreamResult;

import java.util.ArrayList;
import java.util.List;

final class AiSseEventAccumulator {

    private static final TypeReference<List<AiKnowledgeSource>>
            SOURCE_LIST_TYPE = new TypeReference<>() {
            };

    private final ObjectMapper objectMapper;
    private final StringBuilder answer = new StringBuilder();
    private String model;
    private List<AiKnowledgeSource> sources = List.of();

    AiSseEventAccumulator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ParsedEvent accept(List<String> lines)
            throws JsonProcessingException {
        String eventName = "message";
        List<String> dataLines = new ArrayList<>();

        for (String line : lines) {
            if (line.startsWith("event:")) {
                eventName = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                dataLines.add(line.substring(5).stripLeading());
            }
        }

        JsonNode data = dataLines.isEmpty()
                ? objectMapper.createObjectNode()
                : objectMapper.readTree(String.join("\n", dataLines));

        if ("meta".equals(eventName)) {
            model = textOrNull(data.get("model"));
            JsonNode sourceNode = data.get("sources");
            sources = sourceNode == null || sourceNode.isNull()
                    ? List.of()
                    : objectMapper.convertValue(
                            sourceNode,
                            SOURCE_LIST_TYPE
                    );
        } else if ("token".equals(eventName)) {
            String content = textOrNull(data.get("content"));
            if (content != null) {
                answer.append(content);
            }
        } else if ("done".equals(eventName)) {
            String doneModel = textOrNull(data.get("model"));
            if (doneModel != null) {
                model = doneModel;
            }
            return new ParsedEvent(
                    eventName,
                    new AiStreamResult(
                            model,
                            answer.toString(),
                            sources
                    ),
                    null
            );
        } else if ("error".equals(eventName)) {
            return new ParsedEvent(
                    eventName,
                    null,
                    textOrNull(data.get("message"))
            );
        }

        return new ParsedEvent(eventName, null, null);
    }

    private String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    record ParsedEvent(
            String name,
            AiStreamResult completedResult,
            String errorMessage
    ) {
        boolean isDone() {
            return completedResult != null;
        }

        boolean isError() {
            return "error".equals(name);
        }
    }
}
