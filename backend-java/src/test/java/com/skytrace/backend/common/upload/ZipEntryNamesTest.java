package com.skytrace.backend.common.upload;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipEntryNamesTest {

    @Test
    void acceptsRelativeEvidencePath() {
        assertThat(ZipEntryNames.requireSafe("files/EV-001.jpg"))
                .isEqualTo("files/EV-001.jpg");
    }

    @Test
    void rejectsZipSlipAndAbsoluteNames() {
        assertThatThrownBy(() -> ZipEntryNames.requireSafe("../secret.txt"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ZipEntryNames.requireSafe("/etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ZipEntryNames.requireSafe("files/../../tmp/x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
