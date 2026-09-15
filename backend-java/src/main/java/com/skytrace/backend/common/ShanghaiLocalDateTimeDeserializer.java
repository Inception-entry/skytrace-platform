package com.skytrace.backend.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

public class ShanghaiLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {
    @Override
    public LocalDateTime deserialize(
            JsonParser parser,
            DeserializationContext context
    ) throws IOException {
        String text = parser.getValueAsString();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return DatabaseTimes.parseJsonLocalDateTime(text);
        } catch (DateTimeParseException ex) {
            throw context.weirdStringException(
                    text,
                    LocalDateTime.class,
                    ex.getMessage()
            );
        }
    }
}
