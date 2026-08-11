package com.skytrace.backend.evidence.repository;

import com.skytrace.backend.evidence.domain.EvidenceArchiveJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EvidenceArchiveJobRepository
        extends JpaRepository<EvidenceArchiveJob, Long> {

    Optional<EvidenceArchiveJob> findByJobCode(String jobCode);
}
