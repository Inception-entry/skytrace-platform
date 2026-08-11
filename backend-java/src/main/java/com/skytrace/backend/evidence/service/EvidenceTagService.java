package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceTag;
import com.skytrace.backend.evidence.domain.EvidenceTagRel;
import com.skytrace.backend.evidence.dto.EvidenceTagResponse;
import com.skytrace.backend.evidence.repository.EvidenceTagRelRepository;
import com.skytrace.backend.evidence.repository.EvidenceTagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EvidenceTagService {

    private final EvidenceTagRepository tagRepository;
    private final EvidenceTagRelRepository relRepository;

    public EvidenceTagService(
            EvidenceTagRepository tagRepository,
            EvidenceTagRelRepository relRepository) {
        this.tagRepository = tagRepository;
        this.relRepository = relRepository;
    }

    @Transactional(readOnly = true)
    public List<EvidenceTagResponse> listAll() {
        return tagRepository.findAll().stream()
                .map(t -> new EvidenceTagResponse(t.getId(), t.getName(), t.getColor()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EvidenceTagResponse> tagsOf(Long evidenceId) {
        List<Long> tagIds = relRepository.findByEvidenceId(evidenceId).stream()
                .map(EvidenceTagRel::getTagId)
                .toList();
        if (tagIds.isEmpty()) {
            return List.of();
        }
        return tagRepository.findAllById(tagIds).stream()
                .map(t -> new EvidenceTagResponse(t.getId(), t.getName(), t.getColor()))
                .toList();
    }

    @Transactional
    public void replaceTags(Long evidenceId, List<Long> tagIds) {
        relRepository.deleteByEvidenceId(evidenceId);
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        for (Long tagId : tagIds) {
            relRepository.save(new EvidenceTagRel(evidenceId, tagId));
        }
    }

    @Transactional
    public void addTags(Long evidenceId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        for (Long tagId : tagIds) {
            if (!relRepository.existsByEvidenceIdAndTagId(evidenceId, tagId)) {
                relRepository.save(new EvidenceTagRel(evidenceId, tagId));
            }
        }
    }
}