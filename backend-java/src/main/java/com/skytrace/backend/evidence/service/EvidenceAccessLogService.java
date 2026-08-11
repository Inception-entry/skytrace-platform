package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceAccessAction;
import com.skytrace.backend.evidence.domain.EvidenceAccessLog;
import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.repository.EvidenceAccessLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvidenceAccessLogService {

    private final EvidenceAccessLogRepository repository;
    private final EvidenceActorContextService actorContextService;

    public EvidenceAccessLogService(
            EvidenceAccessLogRepository repository,
            EvidenceActorContextService actorContextService) {
        this.repository = repository;
        this.actorContextService = actorContextService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(EvidenceAsset asset, EvidenceAccessAction action) {
        EvidenceActorContext actor = actorContextService.current();
        repository.save(new EvidenceAccessLog(
                asset.getId(),
                asset.getEvidenceCode(),
                action.name(),
                actor.actorId(),
                actor.username(),
                actor.roles() == null ? "" : actor.roles(),
                actor.requestId(),
                actor.clientIp()
        ));
    }

    public void recordPreview(EvidenceAsset asset) {
        record(asset, EvidenceAccessAction.PREVIEW);
    }

    public void recordDownload(EvidenceAsset asset) {
        record(asset, EvidenceAccessAction.DOWNLOAD);
    }

    public void recordDelete(EvidenceAsset asset) {
        record(asset, EvidenceAccessAction.DELETE);
    }

    public void recordRestore(EvidenceAsset asset) {
        record(asset, EvidenceAccessAction.RESTORE);
    }

    public void recordUpload(EvidenceAsset asset) {
        record(asset, EvidenceAccessAction.UPLOAD);
    }
}