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
                LocalDateTime.of(2030, 1, 1, 8, 0)
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
