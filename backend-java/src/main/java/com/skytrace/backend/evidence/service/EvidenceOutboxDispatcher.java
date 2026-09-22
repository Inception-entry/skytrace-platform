package com.skytrace.backend.evidence.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytrace.backend.evidence.domain.EvidenceArchiveJobStatus;
import com.skytrace.backend.evidence.domain.EvidenceOutboxKind;
import com.skytrace.backend.evidence.domain.EvidenceOutbox;
import com.skytrace.backend.evidence.domain.EvidenceOutboxStatus;
import com.skytrace.backend.evidence.dto.EvidenceMinioDeletePayload;
import com.skytrace.backend.evidence.repository.EvidenceArchiveJobRepository;
import com.skytrace.backend.evidence.repository.EvidenceOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
@ConditionalOnProperty(
        name = "app.evidence.outbox.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class EvidenceOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EvidenceOutboxDispatcher.class);

    private final EvidenceOutboxRepository repository;
    private final EvidenceDerivativeJobService derivativeJobService;
    private final ObjectProvider<EvidenceArchiveWorkflowStarter> archiveStarter;
    private final EvidenceArchiveJobRepository archiveJobRepository;
    private final ObjectProvider<EvidenceStorageService> storageService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;
    private final int maxAttempts;

    public EvidenceOutboxDispatcher(
            EvidenceOutboxRepository repository,
            EvidenceDerivativeJobService derivativeJobService,
            ObjectProvider<EvidenceArchiveWorkflowStarter> archiveStarter,
            EvidenceArchiveJobRepository archiveJobRepository,
            ObjectProvider<EvidenceStorageService> storageService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Value("${app.evidence.outbox.batch-size:50}") int batchSize,
            @Value("${app.evidence.outbox.max-attempts:8}") int maxAttempts) {
        this.repository = repository;
        this.derivativeJobService = derivativeJobService;
        this.archiveStarter = archiveStarter;
        this.archiveJobRepository = archiveJobRepository;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    public void dispatchByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            transactionTemplate.executeWithoutResult(status -> dispatchOne(id));
        }
    }

    @Scheduled(fixedDelayString = "${app.evidence.outbox.drain-ms:2000}")
    public void drain() {
        List<EvidenceOutbox> batch = transactionTemplate.execute(status ->
                repository.findByStatusAndAvailableAtLessThanEqualOrderByIdAsc(
                        EvidenceOutboxStatus.PENDING,
                        LocalDateTime.now(),
                        PageRequest.of(0, batchSize)
                )
        );
        if (batch == null || batch.isEmpty()) {
            return;
        }
        for (EvidenceOutbox row : batch) {
            transactionTemplate.executeWithoutResult(status -> dispatchOne(row.getId()));
        }
    }

    void dispatchOne(Long id) {
        repository.lockById(id).ifPresent(this::tryDispatch);
    }

    void tryDispatch(EvidenceOutbox row) {
        if (row.getStatus() != EvidenceOutboxStatus.PENDING) {
            return;
        }
        if (row.getAvailableAt() != null && row.getAvailableAt().isAfter(LocalDateTime.now())) {
            return;
        }
        try {
            switch (row.getKind()) {
                case DERIVATIVE -> derivativeJobService.start(row.getAggregateCode());
                case ARCHIVE -> startArchive(row.getAggregateCode());
                case MINIO_DELETE -> deleteObject(row);
            }
            row.markSent();
            repository.save(row);
            log.info(
                    "event=evidence_outbox_sent id={} aggregateCode={} kind={}",
                    row.getId(),
                    row.getAggregateCode(),
                    row.getKind()
            );
        } catch (RuntimeException exception) {
            int nextAttempts = row.getAttempts() + 1;
            row.markFailedAttempt(
                    maxAttempts,
                    LocalDateTime.now().plusSeconds(backoffSeconds(nextAttempts))
            );
            repository.save(row);
            if (row.getStatus() == EvidenceOutboxStatus.FAILED
                    && row.getKind() == EvidenceOutboxKind.ARCHIVE) {
                markArchiveJobFailed(row.getAggregateCode(), exception.getMessage());
            }
            log.warn(
                    "event=evidence_outbox_retry id={} aggregateCode={} kind={} attempts={} status={} reason={}",
                    row.getId(),
                    row.getAggregateCode(),
                    row.getKind(),
                    row.getAttempts(),
                    row.getStatus(),
                    exception.toString()
            );
        }
    }

    private void startArchive(String jobCode) {
        EvidenceArchiveWorkflowStarter starter = archiveStarter.getIfAvailable();
        if (starter == null) {
            throw new IllegalStateException("archive workflow starter unavailable");
        }
        starter.start(jobCode);
    }

    private void deleteObject(EvidenceOutbox row) {
        EvidenceStorageService storage = storageService.getIfAvailable();
        if (storage == null) {
            throw new IllegalStateException("evidence storage unavailable");
        }
        EvidenceMinioDeletePayload payload = readJson(
                row.getPayload(),
                EvidenceMinioDeletePayload.class
        );
        storage.removeEvidenceObject(payload.bucket(), payload.objectKey());
    }

    private void markArchiveJobFailed(String jobCode, String reason) {
        archiveJobRepository.findByJobCode(jobCode).ifPresent(job -> {
            if (job.getStatus() != EvidenceArchiveJobStatus.PENDING) {
                return;
            }
            job.setStatus(EvidenceArchiveJobStatus.FAILED);
            job.setErrorMessage(truncate(reason));
            archiveJobRepository.save(job);
        });
    }

    private <T> T readJson(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("evidence outbox payload", exception);
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 512 ? value : value.substring(0, 512);
    }

    static long backoffSeconds(int attempts) {
        return Math.min(60L, 1L << Math.min(Math.max(attempts, 1), 6));
    }
}
