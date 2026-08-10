package com.skytrace.backend.evidence.repository;

import com.skytrace.backend.evidence.domain.EvidenceTagRel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceTagRelRepository
        extends JpaRepository<EvidenceTagRel, EvidenceTagRel.PK> {

    List<EvidenceTagRel> findByEvidenceId(Long evidenceId);

    void deleteByEvidenceId(Long evidenceId);

    boolean existsByEvidenceIdAndTagId(Long evidenceId, Long tagId);
}
