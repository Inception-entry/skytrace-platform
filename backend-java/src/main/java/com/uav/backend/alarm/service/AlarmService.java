package com.uav.backend.alarm.service;

import com.uav.backend.alarm.domain.AlarmEvent;
import com.uav.backend.alarm.dto.AlarmResponse;
import com.uav.backend.alarm.dto.CreateAlarmRequest;
import com.uav.backend.alarm.repository.AlarmEventRepository;
import com.uav.backend.cache.AlarmRecentCache;
import com.uav.backend.messaging.AlarmRealtimePublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class AlarmService {
    private final AlarmEventRepository alarmEventRepository;
    private final ObjectProvider<AlarmRealtimePublisher> realtimePublisher;
    private final ObjectProvider<InspectionAlarmSignaler> alarmSignaler;
    private final ObjectProvider<AlarmRecentCache> alarmRecentCache;

    public AlarmService(
            AlarmEventRepository alarmEventRepository,
            ObjectProvider<AlarmRealtimePublisher> realtimePublisher,
            ObjectProvider<InspectionAlarmSignaler> alarmSignaler,
            ObjectProvider<AlarmRecentCache> alarmRecentCache) {
        this.alarmEventRepository = alarmEventRepository;
        this.realtimePublisher = realtimePublisher;
        this.alarmSignaler = alarmSignaler;
        this.alarmRecentCache = alarmRecentCache;
    }

    @Transactional
    public AlarmResponse create(CreateAlarmRequest request) {
        // HTTP path: Node BFF broadcasts Socket.IO; Temporal Signal still runs.
        return create(request, true, false);
    }

    @Transactional
    public AlarmResponse create(
            CreateAlarmRequest request,
            boolean signalWorkflow,
            boolean publishRealtime) {
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
        event.setEventTime(request.eventTime());
        AlarmResponse response = toResponse(alarmEventRepository.save(event));
        evictAlarmCacheAfterCommit();
        if (signalWorkflow) {
            alarmSignaler.ifAvailable(signaler ->
                    signaler.signalAlarmDetected(
                            response.taskCode(),
                            response.eventCode()
                    )
            );
        }
        if (publishRealtime) {
            realtimePublisher.ifAvailable(publisher ->
                    publisher.publishCreated(response)
            );
        }
        return response;
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
                event.getStatus(),
                event.getEventTime()
        );
    }
}
