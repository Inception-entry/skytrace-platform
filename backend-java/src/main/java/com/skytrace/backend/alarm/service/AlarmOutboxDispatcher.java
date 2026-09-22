package com.skytrace.backend.alarm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytrace.backend.alarm.domain.AlarmOutbox;
import com.skytrace.backend.alarm.domain.AlarmOutboxStatus;
import com.skytrace.backend.alarm.dto.AlarmResponse;
import com.skytrace.backend.alarm.dto.AlarmTemporalSignalPayload;
import com.skytrace.backend.alarm.repository.AlarmOutboxRepository;
import com.skytrace.backend.messaging.AlarmRealtimePublisher;
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
        name = "app.alarm.outbox.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AlarmOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AlarmOutboxDispatcher.class);

    private final AlarmOutboxRepository repository;
    private final ObjectProvider<InspectionAlarmSignaler> alarmSignaler;
    private final ObjectProvider<AlarmRealtimePublisher> realtimePublisher;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;
    private final int maxAttempts;

    public AlarmOutboxDispatcher(
            AlarmOutboxRepository repository,
            ObjectProvider<InspectionAlarmSignaler> alarmSignaler,
            ObjectProvider<AlarmRealtimePublisher> realtimePublisher,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Value("${app.alarm.outbox.batch-size:50}") int batchSize,
            @Value("${app.alarm.outbox.max-attempts:8}") int maxAttempts) {
        this.repository = repository;
        this.alarmSignaler = alarmSignaler;
        this.realtimePublisher = realtimePublisher;
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

    @Scheduled(fixedDelayString = "${app.alarm.outbox.drain-ms:2000}")
    public void drain() {
        List<AlarmOutbox> batch = transactionTemplate.execute(status ->
                repository.findByStatusAndAvailableAtLessThanEqualOrderByIdAsc(
                        AlarmOutboxStatus.PENDING,
                        LocalDateTime.now(),
                        PageRequest.of(0, batchSize)
                )
        );
        if (batch == null || batch.isEmpty()) {
            return;
        }
        for (AlarmOutbox row : batch) {
            transactionTemplate.executeWithoutResult(status -> dispatchOne(row.getId()));
        }
    }

    void dispatchOne(Long id) {
        repository.lockById(id).ifPresent(this::tryDispatch);
    }

    void tryDispatch(AlarmOutbox row) {
        if (row.getStatus() != AlarmOutboxStatus.PENDING) {
            return;
        }
        if (row.getAvailableAt() != null && row.getAvailableAt().isAfter(LocalDateTime.now())) {
            return;
        }
        try {
            switch (row.getKind()) {
                case TEMPORAL_SIGNAL -> deliverTemporal(row);
                case REALTIME -> deliverRealtime(row);
            }
            row.markSent();
            repository.save(row);
            log.info(
                    "event=alarm_outbox_sent id={} eventCode={} kind={}",
                    row.getId(),
                    row.getEventCode(),
                    row.getKind()
            );
        } catch (RuntimeException exception) {
            int nextAttempts = row.getAttempts() + 1;
            row.markFailedAttempt(
                    maxAttempts,
                    LocalDateTime.now().plusSeconds(backoffSeconds(nextAttempts))
            );
            repository.save(row);
            log.warn(
                    "event=alarm_outbox_retry id={} eventCode={} kind={} attempts={} status={} reason={}",
                    row.getId(),
                    row.getEventCode(),
                    row.getKind(),
                    row.getAttempts(),
                    row.getStatus(),
                    exception.toString()
            );
        }
    }

    private void deliverTemporal(AlarmOutbox row) {
        InspectionAlarmSignaler signaler = alarmSignaler.getIfAvailable();
        if (signaler == null) {
            throw new IllegalStateException("alarm signaler unavailable");
        }
        AlarmTemporalSignalPayload payload = readJson(
                row.getPayload(),
                AlarmTemporalSignalPayload.class
        );
        signaler.signalAlarmDetected(payload.taskCode(), payload.eventCode());
    }

    private void deliverRealtime(AlarmOutbox row) {
        AlarmRealtimePublisher publisher = realtimePublisher.getIfAvailable();
        if (publisher == null) {
            throw new IllegalStateException("realtime publisher unavailable");
        }
        publisher.publishCreated(readJson(row.getPayload(), AlarmResponse.class));
    }

    private <T> T readJson(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("alarm outbox payload", exception);
        }
    }

    static long backoffSeconds(int attempts) {
        return Math.min(60L, 1L << Math.min(Math.max(attempts, 1), 6));
    }
}
