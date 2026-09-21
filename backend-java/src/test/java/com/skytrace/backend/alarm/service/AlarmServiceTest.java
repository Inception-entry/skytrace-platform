package com.skytrace.backend.alarm.service;

import com.skytrace.backend.alarm.domain.AlarmEvent;
import com.skytrace.backend.alarm.dto.AlarmResponse;
import com.skytrace.backend.alarm.dto.CreateAlarmRequest;
import com.skytrace.backend.alarm.repository.AlarmEventRepository;
import com.skytrace.backend.messaging.AlarmRealtimePublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlarmServiceTest {

    @Test
    void shouldSignalWorkflowButSkipRealtimeOnHttpCreate() {
        AlarmEventRepository repository = mock(AlarmEventRepository.class);
        AlarmRealtimePublisher publisher = mock(AlarmRealtimePublisher.class);
        InspectionAlarmSignaler signaler = mock(InspectionAlarmSignaler.class);
        ObjectProvider<AlarmRealtimePublisher> publisherProvider =
                mockProvider(publisher);
        ObjectProvider<InspectionAlarmSignaler> signalerProvider =
                mockProvider(signaler);
        when(repository.save(any(AlarmEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AlarmService service = new AlarmService(
                repository,
                publisherProvider,
                signalerProvider,
                emptyProvider()
        );
        AlarmResponse response = service.create(sampleRequest());

        assertThat(response.taskCode()).isEqualTo("TASK-1");
        verify(signaler).signalAlarmDetected("TASK-1", response.eventCode());
        verify(publisher, never()).publishCreated(any());
    }

    @Test
    void shouldSkipAllSideEffectsWhenDisabled() {
        AlarmEventRepository repository = mock(AlarmEventRepository.class);
        AlarmRealtimePublisher publisher = mock(AlarmRealtimePublisher.class);
        InspectionAlarmSignaler signaler = mock(InspectionAlarmSignaler.class);
        ObjectProvider<AlarmRealtimePublisher> publisherProvider =
                mockProvider(publisher);
        ObjectProvider<InspectionAlarmSignaler> signalerProvider =
                mockProvider(signaler);
        when(repository.save(any(AlarmEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AlarmService service = new AlarmService(
                repository,
                publisherProvider,
                signalerProvider,
                emptyProvider()
        );
        service.create(sampleRequest(), false, false);

        verify(signaler, never()).signalAlarmDetected(any(), any());
        verify(publisher, never()).publishCreated(any());
    }

    @Test
    void shouldReuseExistingAlarmForSameDetectionId() {
        AlarmEventRepository repository = mock(AlarmEventRepository.class);
        AlarmRealtimePublisher publisher = mock(AlarmRealtimePublisher.class);
        InspectionAlarmSignaler signaler = mock(InspectionAlarmSignaler.class);
        ObjectProvider<AlarmRealtimePublisher> publisherProvider =
                mockProvider(publisher);
        ObjectProvider<InspectionAlarmSignaler> signalerProvider =
                mockProvider(signaler);
        AlarmEvent existing = new AlarmEvent();
        existing.setEventCode("ALARM-EXISTING");
        existing.setDeviceCode("UAV-1");
        existing.setTaskCode("TASK-1");
        existing.setEventType("WEAPON_DETECTED");
        existing.setEventTime(LocalDateTime.of(2030, 1, 1, 8, 0));
        existing.setSourceDetectionId("550e8400-e29b-41d4-a716-446655440000");
        when(repository.findBySourceDetectionId(
                "550e8400-e29b-41d4-a716-446655440000"
        )).thenReturn(java.util.Optional.of(existing));

        AlarmService service = new AlarmService(
                repository,
                publisherProvider,
                signalerProvider,
                emptyProvider()
        );
        AlarmCreateResult result = service.createResult(
                sampleRequestWithDetectionId(
                        "550e8400-e29b-41d4-a716-446655440000"
                ),
                true,
                false
        );

        assertThat(result.inserted()).isFalse();
        assertThat(result.alarm().eventCode()).isEqualTo("ALARM-EXISTING");
        verify(repository, never()).save(any());
        verify(signaler, never()).signalAlarmDetected(any(), any());
        verify(publisher, never()).publishCreated(any());
    }

    @Test
    void shouldInsertWhenDetectionIdIsAbsent() {
        AlarmEventRepository repository = mock(AlarmEventRepository.class);
        AlarmRealtimePublisher publisher = mock(AlarmRealtimePublisher.class);
        InspectionAlarmSignaler signaler = mock(InspectionAlarmSignaler.class);
        when(repository.save(any(AlarmEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AlarmService service = new AlarmService(
                repository,
                mockProvider(publisher),
                mockProvider(signaler),
                emptyProvider()
        );
        AlarmCreateResult first = service.createResult(
                sampleRequest(),
                false,
                false
        );
        AlarmCreateResult second = service.createResult(
                sampleRequest(),
                false,
                false
        );

        assertThat(first.inserted()).isTrue();
        assertThat(second.inserted()).isTrue();
        verify(repository, org.mockito.Mockito.times(2)).save(any(AlarmEvent.class));
    }

    private static CreateAlarmRequest sampleRequestWithDetectionId(String detectionId) {
        return new CreateAlarmRequest(
                "UAV-1",
                "TASK-1",
                "WEAPON_DETECTED",
                "KNIFE",
                BigDecimal.valueOf(0.9),
                BigDecimal.ONE,
                BigDecimal.TEN,
                "task/a.jpg",
                null,
                null,
                null,
                LocalDateTime.of(2030, 1, 1, 8, 0),
                detectionId
        );
    }

    private static CreateAlarmRequest sampleRequest() {
        return new CreateAlarmRequest(
                "UAV-1",
                "TASK-1",
                "WEAPON_DETECTED",
                "KNIFE",
                BigDecimal.valueOf(0.9),
                BigDecimal.ONE,
                BigDecimal.TEN,
                "task/a.jpg",
                null,
                null,
                null,
                LocalDateTime.of(2030, 1, 1, 8, 0),
                null
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> mockProvider(T bean) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        doAnswer(invocation -> {
            Consumer<T> consumer = invocation.getArgument(0);
            consumer.accept(bean);
            return null;
        }).when(provider).ifAvailable(ArgumentMatchers.any());
        return provider;
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
