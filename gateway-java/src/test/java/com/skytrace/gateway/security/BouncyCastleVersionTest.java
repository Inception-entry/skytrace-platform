package com.skytrace.gateway.security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BouncyCastleVersionTest {

    @Test
    void providerClosesNameConstraintAndAsn1Advisories() {
        double version = new BouncyCastleProvider().getVersion();
        assertThat(version)
                .as("CVE-2026-8763 / CVE-2026-13506 need bcprov >= 1.85")
                .isGreaterThanOrEqualTo(1.85);
    }
}
