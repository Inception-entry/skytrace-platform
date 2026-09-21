package com.skytrace.backend.db;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.temporal.test-server.enabled=true",
        "app.security.enabled=false"
})
@ActiveProfiles("flyway-smoke")
@Testcontainers(disabledWithoutDocker = true)
class FlywayEmptyMysqlSchemaTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("skytrace_inspection")
            .withUsername("skytrace_user")
            .withPassword("skytrace-test");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> {
            String url = MYSQL.getJdbcUrl();
            String extra = "connectionCollation=utf8mb4_unicode_ci"
                    + "&serverTimezone=Asia/Shanghai"
                    + "&useSSL=false"
                    + "&allowPublicKeyRetrieval=true";
            return url + (url.contains("?") ? "&" : "?") + extra;
        });
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add(
                "spring.datasource.driver-class-name",
                () -> "com.mysql.cj.jdbc.Driver"
        );
    }

    @Autowired
    private DataSource dataSource;

    @Test
    void emptyMysqlMigratesAndValidatesInspectionTask() throws Exception {
        try (Connection connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData().getTables(
                     connection.getCatalog(),
                     null,
                     "inspection_task",
                     new String[] {"TABLE"}
             )) {
            assertThat(tables.next()).isTrue();
        }
        try (Connection connection = dataSource.getConnection();
             ResultSet column = connection.getMetaData().getColumns(
                     connection.getCatalog(),
                     null,
                     "alarm_event",
                     "source_detection_id"
             )) {
            assertThat(column.next()).isTrue();
        }
        try (Connection connection = dataSource.getConnection();
             ResultSet history = connection.createStatement().executeQuery(
                     "SELECT version FROM flyway_schema_history WHERE version = '22'"
             )) {
            assertThat(history.next()).isTrue();
        }
        try (Connection connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData().getTables(
                     connection.getCatalog(),
                     null,
                     "alarm_outbox",
                     new String[] {"TABLE"}
             )) {
            assertThat(tables.next()).isTrue();
        }
    }
}
