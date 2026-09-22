package com.skytrace.backend.evidence.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytrace.backend.evidence.domain.EvidenceOutbox;
import com.skytrace.backend.evidence.domain.EvidenceOutboxKind;
import com.skytrace.backend.evidence.dto.EvidenceMinioDeletePayload;
import com.skytrace.backend.evidence.dto.EvidenceWorkflowPayload;
import com.skytrace.backend.evidence.repository.EvidenceOutboxRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Service
public class EvidenceOutboxWriter {

    private final EvidenceOutboxRepository repository;
    private final ObjectProvider<EvidenceOutboxDispatcher> outboxDispatcher;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate orphanTemplate;

    public EvidenceOutboxWriter(
            EvidenceOutboxRepository repository,
            ObjectProvider<EvidenceOutboxDispatcher> outboxDispatcher,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.outboxDispatcher = outboxDispatcher;
        this.objectMapper = objectMapper;
        this.orphanTemplate = new TransactionTemplate(transactionManager);
        this.orphanTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    public void enqueueDerivative(String evidenceCode) {
        dispatchAfterCommit(save(evidenceCode, EvidenceOutboxKind.DERIVATIVE));
    }

    public void enqueueArchive(String jobCode) {
        dispatchAfterCommit(save(jobCode, EvidenceOutboxKind.ARCHIVE));
    }

    public void recordOrphanDelete(String bucket, String objectKey) {
        Long id = orphanTemplate.execute(status ->
                repository.saveAndFlush(new EvidenceOutbox(
                        objectKey,
                        EvidenceOutboxKind.MINIO_DELETE,
                        writeJson(new EvidenceMinioDeletePayload(bucket, objectKey))
                )).getId()
        );
        dispatchNow(id);
    }

    private Long save(String aggregateCode, EvidenceOutboxKind kind) {
        return repository.saveAndFlush(new EvidenceOutbox(
                aggregateCode,
                kind,
                writeJson(new EvidenceWorkflowPayload(aggregateCode))
        )).getId();
    }

    private void dispatchAfterCommit(Long id) {
        if (id == null) {
            return;
        }
        EvidenceOutboxDispatcher dispatcher = outboxDispatcher.getIfAvailable();
        if (dispatcher == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatcher.dispatchByIds(List.of(id));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        dispatcher.dispatchByIds(List.of(id));
                    }
                }
        );
    }

    private void dispatchNow(Long id) {
        if (id == null) {
            return;
        }
        EvidenceOutboxDispatcher dispatcher = outboxDispatcher.getIfAvailable();
        if (dispatcher != null) {
            dispatcher.dispatchByIds(List.of(id));
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("evidence outbox payload", exception);
        }
    }
}
