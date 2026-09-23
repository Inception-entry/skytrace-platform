package com.skytrace.backend.alarm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytrace.backend.alarm.domain.AlarmEvent;
import com.skytrace.backend.alarm.domain.AlarmOutbox;
import com.skytrace.backend.alarm.domain.AlarmOutboxKind;
import com.skytrace.backend.alarm.dto.AlarmResponse;
import com.skytrace.backend.alarm.dto.AlarmTemporalSignalPayload;
import com.skytrace.backend.alarm.dto.CreateAlarmRequest;
import com.skytrace.backend.alarm.repository.AlarmEventRepository;
import com.skytrace.backend.alarm.repository.AlarmOutboxRepository;
import com.skytrace.backend.cache.AlarmRecentCache;
import com.skytrace.backend.common.DatabaseTimes;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AlarmService {
    private final AlarmEventRepository alarmEventRepository;
    private final AlarmOutboxRepository alarmOutboxRepository;
    private final ObjectProvider<AlarmOutboxDispatcher> outboxDispatcher;
    private final ObjectProvider<AlarmRecentCache> alarmRecentCache;
    private final ObjectMapper objectMapper;

    public AlarmService(
            AlarmEventRepository alarmEventRepository,
            AlarmOutboxRepository alarmOutboxRepository,
            ObjectProvider<AlarmOutboxDispatcher> outboxDispatcher,
            ObjectProvider<AlarmRecentCache> alarmRecentCache,
            ObjectMapper objectMapper) {
        this.alarmEventRepository = alarmEventRepository;
        this.alarmOutboxRepository = alarmOutboxRepository;
        this.outboxDispatcher = outboxDispatcher;
        this.alarmRecentCache = alarmRecentCache;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AlarmResponse create(CreateAlarmRequest request) {
        // HTTP path: Node BFF broadcasts Socket.IO; Temporal Signal still runs via outbox.
        return create(request, true, false);
    }

    @Transactional
    public AlarmResponse create(
            CreateAlarmRequest request,
            boolean signalWorkflow,
            boolean publishRealtime) {
        return createResult(request, signalWorkflow, publishRealtime).alarm();
    }

    @Transactional
    public AlarmCreateResult createResult(
            CreateAlarmRequest request,
            boolean signalWorkflow,
            boolean publishRealtime) {
        String detectionId = normalizeDetectionId(request.detectionId());
        if (detectionId != null) {
            var existing = alarmEventRepository.findBySourceDetectionId(detectionId);
            if (existing.isPresent()) {
                return new AlarmCreateResult(toResponse(existing.get()), false);
            }
        }
        AlarmEvent event = new AlarmEvent();
        event.setEventCode(
                "ALARM-"
                        + DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                        .format(request.eventTime())
                        + "-"
                        + UUID.randomUUID().toString().substring(0, 8)
        );
        event.setDeviceCode(request.deviceCode());
        event.setTaskCode(request.taskCode());
        event.setEventType(request.eventType());
        event.setWeaponType(request.weaponType());
        event.setConfidence(request.confidence());
        event.setLatitude(request.latitude());
        event.setLongitude(request.longitude());
        event.setImageUrl(request.imageUrl());
        event.setVideoUrl(request.videoUrl());
        event.setPrimaryEvidenceCode(request.primaryEvidenceCode());
        event.setPrimaryVideoEvidenceCode(request.primaryVideoEvidenceCode());
        event.setEventTime(request.eventTime());
        event.setEventInstantUtc(DatabaseTimes.toUtcInstantString(request.eventTime()));
        event.setSourceDetectionId(detectionId);
        AlarmResponse response = toResponse(alarmEventRepository.save(event));
        evictAlarmCacheAfterCommit();
        enqueueSideEffects(response, signalWorkflow, publishRealtime);
        return new AlarmCreateResult(response, true);
    }

    @Transactional(readOnly = true)
    public boolean existsByDetectionId(String detectionId) {
        String normalized = normalizeDetectionId(detectionId);
        return normalized != null
                && alarmEventRepository.existsBySourceDetectionId(normalized);
    }

    @Transactional(readOnly = true)
    public List<AlarmResponse> latest() {
        AlarmRecentCache cache = alarmRecentCache.getIfAvailable();
        if (cache != null) {
            var cached = cache.get();
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        List<AlarmResponse> latest = alarmEventRepository
                .findTop20ByOrderByEventTimeDesc()
                .stream()
                .map(this::toResponse)
                .toList();
        if (cache != null && !latest.isEmpty()) {
            cache.put(latest);
        }
        return latest;
    }

    private void enqueueSideEffects(
            AlarmResponse response,
            boolean signalWorkflow,
            boolean publishRealtime) {
        List<Long> ids = new ArrayList<>();
        if (signalWorkflow) {
            ids.add(saveOutbox(
                    response.eventCode(),
                    AlarmOutboxKind.TEMPORAL_SIGNAL,
                    writeJson(new AlarmTemporalSignalPayload(
                            response.taskCode(),
                            response.eventCode()
                    ))
            ));
        }
        if (publishRealtime) {
            ids.add(saveOutbox(
                    response.eventCode(),
                    AlarmOutboxKind.REALTIME,
                    writeJson(response)
            ));
        }
        dispatchAfterCommit(ids.stream().filter(id -> id != null).toList());
    }

    private Long saveOutbox(String eventCode, AlarmOutboxKind kind, String payload) {
        return alarmOutboxRepository
                .saveAndFlush(new AlarmOutbox(eventCode, kind, payload))
                .getId();
    }

    private void dispatchAfterCommit(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        AlarmOutboxDispatcher dispatcher = outboxDispatcher.getIfAvailable();
        if (dispatcher == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatcher.dispatchByIds(ids);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        dispatcher.dispatchByIds(ids);
                    }
                }
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("alarm outbox payload", exception);
        }
    }

    private void evictAlarmCacheAfterCommit() {
        AlarmRecentCache cache = alarmRecentCache.getIfAvailable();
        if (cache == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cache.evict();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        cache.evict();
                    }
                }
        );
    }

    private static String eventInstantUtc(AlarmEvent event) {
        if (event.getEventInstantUtc() != null && !event.getEventInstantUtc().isBlank()) {
            return event.getEventInstantUtc();
        }
        return DatabaseTimes.toUtcInstantString(event.getEventTime());
    }

    private static String normalizeDetectionId(String detectionId) {
        if (detectionId == null || detectionId.isBlank()) {
            return null;
        }
        String trimmed = detectionId.trim();
        try {
            return UUID.fromString(trimmed).toString();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private AlarmResponse toResponse(AlarmEvent event) {
        return new AlarmResponse(
                event.getId(),
                event.getEventCode(),
                event.getDeviceCode(),
                event.getTaskCode(),
                event.getEventType(),
                event.getWeaponType(),
                event.getConfidence(),
                event.getLatitude(),
                event.getLongitude(),
                event.getImageUrl(),
                event.getVideoUrl(),
                event.getPrimaryEvidenceCode(),
                event.getPrimaryVideoEvidenceCode(),
                event.getStatus(),
                event.getEventTime(),
                eventInstantUtc(event)
        );
    }
}
