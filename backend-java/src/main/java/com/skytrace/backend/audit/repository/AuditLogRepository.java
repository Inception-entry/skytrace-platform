package com.skytrace.backend.audit.repository;

import com.skytrace.backend.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuditLogRepository
        extends JpaRepository<AuditLog, Long> {

    @Query("""
            SELECT audit
            FROM AuditLog audit
            WHERE (:action IS NULL OR audit.action = :action)
              AND (:outcome IS NULL OR audit.outcome = :outcome)
              AND (:username IS NULL
                   OR LOWER(audit.username) LIKE LOWER(CONCAT('%', :username, '%')))
            """)
    Page<AuditLog> search(
            @Param("action") String action,
            @Param("outcome") String outcome,
            @Param("username") String username,
            Pageable pageable
    );

    long countByOutcomeAndCreatedAtAfter(
            String outcome,
            LocalDateTime createdAt
    );
}
