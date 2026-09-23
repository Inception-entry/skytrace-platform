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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DetectionAlarmListenerTest {

    private static final String DETECTION_ID =
            "550e8400-e29b-41d4-a716-446655440000";

    @Test
    void duplicateDetectionSkipsCreate() {
        AlarmService alarmService = mock(AlarmService.class);
        when(alarmService.existsByDetectionId(DETECTION_ID)).thenReturn(true);

        listener(alarmService).onDetection(sampleMessage());

        verify(alarmService, never()).createResult(any(), anyBoolean(), anyBoolean());
    }

    @Test
    void firstInsertEnqueuesRealtimeOutbox() {
        AlarmService alarmService = mock(AlarmService.class);
        AlarmResponse alarm = sampleAlarm();
        when(alarmService.existsByDetectionId(DETECTION_ID)).thenReturn(false);
        when(alarmService.createResult(any(), anyBoolean(), anyBoolean()))
                .thenReturn(new AlarmCreateResult(alarm, true));

        listener(alarmService).onDetection(sampleMessage());

        verify(alarmService).createResult(any(), eq(true), eq(true));
    }

    @Test
    void uniqueViolationDoesNotRetryCreate() {
        AlarmService alarmService = mock(AlarmService.class);
        when(alarmService.existsByDetectionId(DETECTION_ID)).thenReturn(false);
        when(alarmService.createResult(any(), anyBoolean(), anyBoolean()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        listener(alarmService).onDetection(sampleMessage());

        verify(alarmService).createResult(any(), eq(true), eq(true));
    }

    private static DetectionAlarmListener listener(AlarmService alarmService) {
        return new DetectionAlarmListener(
                alarmService,
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
                LocalDateTime.of(2030, 1, 1, 8, 0),
                "2030-01-01T00:00:00Z"
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
