package com.skytrace.backend.alarm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.skytrace.backend.alarm.domain.AlarmEvent;
import com.skytrace.backend.alarm.domain.AlarmOutbox;
import com.skytrace.backend.alarm.domain.AlarmOutboxKind;
import com.skytrace.backend.alarm.dto.AlarmResponse;
import com.skytrace.backend.alarm.dto.CreateAlarmRequest;
import com.skytrace.backend.alarm.repository.AlarmEventRepository;
import com.skytrace.backend.alarm.repository.AlarmOutboxRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlarmServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void shouldEnqueueTemporalButSkipRealtimeOnHttpCreate() {
        AlarmEventRepository repository = mock(AlarmEventRepository.class);
        AlarmOutboxRepository outboxRepository = mock(AlarmOutboxRepository.class);
        when(repository.save(any(AlarmEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxRepository.saveAndFlush(any(AlarmOutbox.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AlarmService service = new AlarmService(
                repository,
                outboxRepository,
                emptyProvider(),
                emptyProvider(),
                objectMapper
        );
        AlarmResponse response = service.create(sampleRequest());

        assertThat(response.taskCode()).isEqualTo("TASK-1");
        ArgumentCaptor<AlarmOutbox> captor = ArgumentCaptor.forClass(AlarmOutbox.class);
        verify(outboxRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getKind()).isEqualTo(AlarmOutboxKind.TEMPORAL_SIGNAL);
        assertThat(captor.getValue().getEventCode()).isEqualTo(response.eventCode());
        verify(outboxRepository, times(1)).saveAndFlush(any(AlarmOutbox.class));
    }

    @Test
    void shouldSkipAllSideEffectsWhenDisabled() {
        AlarmEventRepository repository = mock(AlarmEventRepository.class);
        AlarmOutboxRepository outboxRepository = mock(AlarmOutboxRepository.class);
        when(repository.save(any(AlarmEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AlarmService service = new AlarmService(
                repository,
                outboxRepository,
                emptyProvider(),
                emptyProvider(),
                objectMapper
        );
        service.create(sampleRequest(), false, false);

        verify(outboxRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldEnqueueTemporalAndRealtimeWhenRequested() {
        AlarmEventRepository repository = mock(AlarmEventRepository.class);
        AlarmOutboxRepository outboxRepository = mock(AlarmOutboxRepository.class);
        when(repository.save(any(AlarmEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxRepository.saveAndFlush(any(AlarmOutbox.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AlarmService service = new AlarmService(
                repository,
                outboxRepository,
                emptyProvider(),
                emptyProvider(),
                objectMapper
        );
        service.createResult(sampleRequest(), true, true);

        ArgumentCaptor<AlarmOutbox> captor = ArgumentCaptor.forClass(AlarmOutbox.class);
        verify(outboxRepository, times(2)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AlarmOutbox::getKind)
                .containsExactly(
                        AlarmOutboxKind.TEMPORAL_SIGNAL,
                        AlarmOutboxKind.REALTIME
                );
    }

    @Test
    void shouldReuseExistingAlarmForSameDetectionId() {
        AlarmEventRepository repository = mock(AlarmEventRepository.class);
        AlarmOutboxRepository outboxRepository = mock(AlarmOutboxRepository.class);
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
                outboxRepository,
                emptyProvider(),
                emptyProvider(),
                objectMapper
        );
        AlarmCreateResult result = service.createResult(
                sampleRequestWithDetectionId(
                        "550e8400-e29b-41d4-a716-446655440000"
                ),
                true,
                true
        );

        assertThat(result.inserted()).isFalse();
        assertThat(result.alarm().eventCode()).isEqualTo("ALARM-EXISTING");
        verify(repository, never()).save(any());
        verify(outboxRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldInsertWhenDetectionIdIsAbsent() {
        AlarmEventRepository repository = mock(AlarmEventRepository.class);
        AlarmOutboxRepository outboxRepository = mock(AlarmOutboxRepository.class);
        when(repository.save(any(AlarmEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AlarmService service = new AlarmService(
                repository,
                outboxRepository,
                emptyProvider(),
                emptyProvider(),
                objectMapper
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
        verify(outboxRepository, never()).saveAndFlush(any());
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
    private static <T> ObjectProvider<T> emptyProvider() {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        doAnswer(invocation -> null)
                .when(provider).ifAvailable(ArgumentMatchers.any());
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}
