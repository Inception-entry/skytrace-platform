package com.skytrace.backend.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiSseEventAccumulatorTest {

    @Test
    void accumulatesMetadataTokensAndCompletedResult() throws Exception {
        AiSseEventAccumulator accumulator =
                new AiSseEventAccumulator(new ObjectMapper());

        accumulator.accept(List.of(
                "event: meta",
                "data: {\"model\":\"skytrace-model\",\"sources\":["
                        + "{\"documentId\":\"doc-1\","
                        + "\"filename\":\"manual.pdf\","
                        + "\"page\":3,\"score\":0.91}]}"
        ));
        accumulator.accept(List.of(
                "event: token",
                "data: {\"content\":\"飞行\"}"
        ));
        accumulator.accept(List.of(
                "event: token",
                "data: {\"content\":\"安全\"}"
        ));
        AiSseEventAccumulator.ParsedEvent done =
                accumulator.accept(List.of(
                        "event: done",
                        "data: {\"model\":\"skytrace-model\","
                                + "\"answerLength\":4}"
                ));

        assertThat(done.isDone()).isTrue();
        assertThat(done.completedResult().answer())
                .isEqualTo("飞行安全");
        assertThat(done.completedResult().model())
                .isEqualTo("skytrace-model");
        assertThat(done.completedResult().sources())
                .singleElement()
                .satisfies(source -> {
                    assertThat(source.documentId()).isEqualTo("doc-1");
                    assertThat(source.filename()).isEqualTo("manual.pdf");
                    assertThat(source.page()).isEqualTo(3);
                });
    }

    @Test
    void recognizesErrorAsTerminalEvent() throws Exception {
        AiSseEventAccumulator accumulator =
                new AiSseEventAccumulator(new ObjectMapper());

        AiSseEventAccumulator.ParsedEvent error =
                accumulator.accept(List.of(
                        "event: error",
                        "data: {\"message\":\"模型不可用\"}"
                ));

        assertThat(error.isError()).isTrue();
        assertThat(error.errorMessage()).isEqualTo("模型不可用");
    }
}
