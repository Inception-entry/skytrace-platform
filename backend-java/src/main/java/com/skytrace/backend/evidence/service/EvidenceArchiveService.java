package com.skytrace.backend.evidence.service;

import com.skytrace.backend.alarm.repository.AlarmEventRepository;
import com.skytrace.backend.evidence.domain.EvidenceArchiveJob;
import com.skytrace.backend.evidence.domain.EvidenceArchiveJobStatus;
import com.skytrace.backend.evidence.domain.EvidenceArchiveScopeType;
import com.skytrace.backend.evidence.dto.CreateEvidenceArchiveJobRequest;
import com.skytrace.backend.evidence.dto.EvidenceArchiveAccessUrlResponse;
import com.skytrace.backend.evidence.dto.EvidenceArchiveJobResponse;
import com.skytrace.backend.evidence.repository.EvidenceArchiveJobRepository;
import com.skytrace.backend.task.repository.InspectionTaskRepository;
import com.skytrace.backend.temporal.workflow.EvidenceArchiveWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.minio.enabled", havingValue = "true")
public class EvidenceArchiveService {

    private static final DateTimeFormatter DAY =
            DateTimeFormatter.BASIC_ISO_DATE;

    private final EvidenceArchiveJobRepository repository;
    private final EvidenceActorContextService actorContextService;
    private final EvidenceStorageService storageService;
    private final WorkflowClient workflowClient;
    private final InspectionTaskRepository inspectionTaskRepository;
    private final AlarmEventRepository alarmEventRepository;
    private final String taskQueue;

    public EvidenceArchiveService(
            EvidenceArchiveJobRepository repository,
            EvidenceActorContextService actorContextService,
            EvidenceStorageService storageService,
            WorkflowClient workflowClient,
            InspectionTaskRepository inspectionTaskRepository,
            AlarmEventRepository alarmEventRepository,
            @Value("${TEMPORAL_TASK_QUEUE:skytrace-inspection-task-queue}")
            String taskQueue) {
        this.repository = repository;
        this.actorContextService = actorContextService;
        this.storageService = storageService;
        this.workflowClient = workflowClient;
        this.inspectionTaskRepository = inspectionTaskRepository;
        this.alarmEventRepository = alarmEventRepository;
        this.taskQueue = taskQueue;
    }

    @Transactional
    public EvidenceArchiveJobResponse createJob(
            CreateEvidenceArchiveJobRequest request) {
        EvidenceArchiveScopeType scopeType = parseScopeType(request.scopeType());
        String scopeValue = requireScopeValue(request.scopeValue());
        validateScope(scopeType, scopeValue);

        EvidenceActorContext actor = actorContextService.current();
        EvidenceArchiveJob job = new EvidenceArchiveJob();
        job.setJobCode(nextJobCode());
        job.setScopeType(scopeType);
        job.setScopeValue(scopeValue);
        job.setStatus(EvidenceArchiveJobStatus.PENDING);
        job.setCreatedBy(actor.actorId());
        job.setCreatedByName(actor.username());
        repository.save(job);

        try {
            EvidenceArchiveWorkflow workflow = workflowClient.newWorkflowStub(
                    EvidenceArchiveWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setTaskQueue(taskQueue)
                            .setWorkflowId("evidence-archive-" + job.getJobCode())
                            .build()
            );
            WorkflowClient.start(workflow::archive, job.getJobCode());
        } catch (Exception exception) {
            job.setStatus(EvidenceArchiveJobStatus.FAILED);
            job.setErrorMessage(truncate(exception.getMessage()));
            repository.save(job);
        }

        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public EvidenceArchiveJobResponse getJob(String jobCode) {
        return toResponse(requireJob(jobCode));
    }

    @Transactional(readOnly = true)
    public EvidenceArchiveAccessUrlResponse createDownloadUrl(String jobCode) {
        EvidenceArchiveJob job = requireCompletedJob(jobCode);
        return buildUrl(
                job.getOutputBucket(),
                job.getOutputObjectKey(),
                "attachment; filename=\"" + filenameOf(job.getOutputObjectKey()) + "\""
        );
    }

    @Transactional(readOnly = true)
    public EvidenceArchiveAccessUrlResponse createManifestUrl(String jobCode) {
        EvidenceArchiveJob job = requireCompletedJob(jobCode);
        return buildUrl(
                job.getOutputBucket(),
                job.getManifestObjectKey(),
                "attachment; filename=\"" + filenameOf(job.getManifestObjectKey()) + "\""
        );
    }

    public EvidenceArchiveJob requireJob(String jobCode) {
        return repository.findByJobCode(normalize(jobCode))
                .orElseThrow(() -> new NoSuchElementException(
                        "归档任务不存在: " + jobCode
                ));
    }

    private EvidenceArchiveJob requireCompletedJob(String jobCode) {
        EvidenceArchiveJob job = requireJob(jobCode);
        if (job.getStatus() != EvidenceArchiveJobStatus.COMPLETED) {
            throw new IllegalStateException("归档任务尚未完成");
        }
        if (job.getOutputBucket() == null || job.getOutputObjectKey() == null) {
            throw new IllegalStateException("归档产物尚未生成");
        }
        return job;
    }

    private EvidenceArchiveAccessUrlResponse buildUrl(
            String bucket,
            String objectKey,
            String contentDisposition) {
        String url = storageService.createPresignedGetUrl(
                bucket,
                objectKey,
                storageService.downloadTtlSeconds(),
                contentDisposition
        );
        Instant expiresAt = storageService.expiresAt(
                storageService.downloadTtlSeconds()
        );
        return new EvidenceArchiveAccessUrlResponse(url, expiresAt);
    }

    private void validateScope(
            EvidenceArchiveScopeType scopeType,
            String scopeValue) {
        switch (scopeType) {
            case TASK -> {
                if (!inspectionTaskRepository.existsByTaskCode(scopeValue)) {
                    throw new NoSuchElementException("巡检任务不存在: " + scopeValue);
                }
            }
            case ALARM -> {
                if (!alarmEventRepository.existsByEventCode(scopeValue)) {
                    throw new NoSuchElementException("告警事件不存在: " + scopeValue);
                }
            }
            case CASE -> throw new IllegalArgumentException(
                    "当前版本暂不支持 CASE 归档范围，请先使用 TASK 或 ALARM"
            );
        }
    }

    private EvidenceArchiveJobResponse toResponse(EvidenceArchiveJob job) {
        return new EvidenceArchiveJobResponse(
                job.getJobCode(),
                job.getScopeType().name(),
                job.getScopeValue(),
                job.getStatus().name(),
                job.getOutputObjectKey(),
                job.getManifestObjectKey(),
                job.getTotalFiles(),
                job.getTotalBytes(),
                toInstant(job.getCreatedAt()),
                job.getCompletedAt() == null ? null : toInstant(job.getCompletedAt()),
                job.getErrorMessage()
        );
    }

    private String nextJobCode() {
        String day = LocalDate.now(ZoneOffset.UTC).format(DAY);
        String suffix = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 6)
                .toUpperCase(Locale.ROOT);
        return "AR-" + day + "-" + suffix;
    }

    private static EvidenceArchiveScopeType parseScopeType(String value) {
        return EvidenceArchiveScopeType.valueOf(normalize(value).toUpperCase(Locale.ROOT));
    }

    private static String requireScopeValue(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException("scopeValue 不能为空");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Instant toInstant(java.time.LocalDateTime value) {
        return value.atZone(ZoneOffset.UTC).toInstant();
    }

    private static String filenameOf(String objectKey) {
        int index = objectKey.lastIndexOf('/');
        return index >= 0 ? objectKey.substring(index + 1) : objectKey;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 512 ? value : value.substring(0, 512);
    }
}
