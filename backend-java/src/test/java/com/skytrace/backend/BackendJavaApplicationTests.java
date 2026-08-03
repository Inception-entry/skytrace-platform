package com.skytrace.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.temporal.test-server.enabled=true"
})
class BackendJavaApplicationTests {
    @Test
    void contextLoads() {}
}
