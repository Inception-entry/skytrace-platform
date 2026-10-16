package com.skytrace.backend.common.upload;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentDispositionNamesTest {

    @Test
    void stripsPathAndHeaderBreaks() {
        assertThat(ContentDispositionNames.attachment(
                "reports/../../a\r\nSet-Cookie: x.jpg"
        )).isEqualTo("attachment; filename=\"a__Set-Cookie__x.jpg\"");
    }
}
