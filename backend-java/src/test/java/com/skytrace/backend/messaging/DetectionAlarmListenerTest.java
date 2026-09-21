package com.skytrace.backend.messaging;

import com.skytrace.backend.alarm.domain.AlarmStatus;
import com.skytrace.backend.alarm.dto.AlarmResponse;
import com.skytrace.backend.alarm.service.AlarmCreateResult;
import com.skytrace.backend.alarm.service.AlarmService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DetectionAlarmListenerTest {

    private static final String DETECTION_ID =
            "550e8400-e29b-41d4-a716-446655440000";

    @Test
    void duplicateDetectionSkipsRealtime() {
        AlarmService alarmService = mock(AlarmService.class);
        AlarmRealtimePublisher realtime = mock(AlarmRealtimePublisher.class);
        when(alarmService.existsByDetectionId(DETECTION_ID)).thenReturn(true);

        listener(alarmService, realtime).onDetection(sampleMessage());

        verify(alarmService, never()).createResult(any(), anyBoolean(), anyBoolean());
        verify(realtime, never()).publishCreated(any());
    }

    @Test
    void firstInsertPublishesRealtime() {
        AlarmService alarmService = mock(AlarmService.class);
        AlarmRealtimePublisher realtime = mock(AlarmRealtimePublisher.class);
        AlarmResponse alarm = sampleAlarm();
        when(alarmService.existsByDetectionId(DETECTION_ID)).thenReturn(false);
        when(alarmService.createResult(any(), anyBoolean(), anyBoolean()))
                .thenReturn(new AlarmCreateResult(alarm, true));

        listener(alarmService, realtime).onDetection(sampleMessage());

        verify(realtime).publishCreated(alarm);
    }

    @Test
    void uniqueViolationSkipsRealtime() {
        AlarmService alarmService = mock(AlarmService.class);
        AlarmRealtimePublisher realtime = mock(AlarmRealtimePublisher.class);
        when(alarmService.existsByDetectionId(DETECTION_ID)).thenReturn(false);
        when(alarmService.createResult(any(), anyBoolean(), anyBoolean()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        listener(alarmService, realtime).onDetection(sampleMessage());

        verify(realtime, never()).publishCreated(any());
    }

    private static DetectionAlarmListener listener(
            AlarmService alarmService,
            AlarmRealtimePublisher realtime) {
        return new DetectionAlarmListener(
                alarmService,
                realtime,
                emptyProvider(),
                emptyProvider()
        );
    }

    private static DetectionAlarmMessage sampleMessage() {
        return new DetectionAlarmMessage(
                "UAV-1",
                "TASK-1",
                "WEAPON_DETECTED",
                "KNIFE",
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.of(2030, 1, 1, 8, 0),
                DETECTION_ID
        );
    }

    private static AlarmResponse sampleAlarm() {
        return new AlarmResponse(
                1L,
                "ALARM-1",
                "UAV-1",
                "TASK-1",
                "WEAPON_DETECTED",
                "KNIFE",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                AlarmStatus.PENDING,
                LocalDateTime.of(2030, 1, 1, 8, 0)
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> emptyProvider() {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        doAnswer(invocation -> null)
                .when(provider).ifAvailable(ArgumentMatchers.any());
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}
