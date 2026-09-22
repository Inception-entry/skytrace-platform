package com.skytrace.backend.evidence.repository;

import com.skytrace.backend.evidence.domain.EvidenceOutbox;
import com.skytrace.backend.evidence.domain.EvidenceOutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EvidenceOutboxRepository extends JpaRepository<EvidenceOutbox, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from EvidenceOutbox o where o.id = :id")
    Optional<EvidenceOutbox> lockById(@Param("id") Long id);

    List<EvidenceOutbox> findByStatusAndAvailableAtLessThanEqualOrderByIdAsc(
            EvidenceOutboxStatus status,
            LocalDateTime availableAt,
            Pageable pageable
    );
}
