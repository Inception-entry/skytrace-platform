package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceTag;
import com.skytrace.backend.evidence.domain.EvidenceTagRel;
import com.skytrace.backend.evidence.dto.EvidenceTagResponse;
import com.skytrace.backend.evidence.repository.EvidenceTagRelRepository;
import com.skytrace.backend.evidence.repository.EvidenceTagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        return tagsOfAll(List.of(evidenceId)).getOrDefault(evidenceId, List.of());
    }

    @Transactional(readOnly = true)
    public Map<Long, List<EvidenceTagResponse>> tagsOfAll(Collection<Long> evidenceIds) {
        if (evidenceIds == null || evidenceIds.isEmpty()) {
            return Map.of();
        }
        List<EvidenceTagRel> relations = relRepository.findByEvidenceIdIn(evidenceIds);
        if (relations.isEmpty()) {
            return Map.of();
        }
        Map<Long, EvidenceTag> tags = new HashMap<>();
        for (EvidenceTag tag : tagRepository.findAllById(
                relations.stream().map(EvidenceTagRel::getTagId).distinct().toList())) {
            tags.put(tag.getId(), tag);
        }
        Map<Long, List<EvidenceTagResponse>> grouped = new HashMap<>();
        for (EvidenceTagRel relation : relations) {
            EvidenceTag tag = tags.get(relation.getTagId());
            if (tag == null) {
                continue;
            }
            grouped.computeIfAbsent(relation.getEvidenceId(), ignored -> new java.util.ArrayList<>())
                    .add(new EvidenceTagResponse(tag.getId(), tag.getName(), tag.getColor()));
        }
        return grouped;
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