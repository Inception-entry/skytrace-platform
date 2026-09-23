package com.skytrace.backend.alarm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.skytrace.backend.alarm.domain.AlarmOutbox;
import com.skytrace.backend.alarm.domain.AlarmOutboxKind;
import com.skytrace.backend.alarm.domain.AlarmOutboxStatus;
import com.skytrace.backend.alarm.domain.AlarmStatus;
import com.skytrace.backend.alarm.dto.AlarmResponse;
import com.skytrace.backend.alarm.dto.AlarmTemporalSignalPayload;
import com.skytrace.backend.alarm.repository.AlarmOutboxRepository;
import com.skytrace.backend.messaging.AlarmRealtimePublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlarmOutboxDispatcherTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void temporalSuccessMarksSent() throws Exception {
        AlarmOutboxRepository repository = mock(AlarmOutboxRepository.class);
        InspectionAlarmSignaler signaler = mock(InspectionAlarmSignaler.class);
        AlarmOutbox row = new AlarmOutbox(
                "ALARM-1",
                AlarmOutboxKind.TEMPORAL_SIGNAL,
                objectMapper.writeValueAsString(
                        new AlarmTemporalSignalPayload("TASK-1", "ALARM-1")
                )
        );

        dispatcher(repository, signaler, null).tryDispatch(row);

        verify(signaler).signalAlarmDetected("TASK-1", "ALARM-1");
        verify(repository).save(row);
        assertThat(row.getStatus()).isEqualTo(AlarmOutboxStatus.SENT);
        assertThat(row.getSentAt()).isNotNull();
    }

    @Test
    void temporalFailureRetriesThenFails() throws Exception {
        AlarmOutboxRepository repository = mock(AlarmOutboxRepository.class);
        InspectionAlarmSignaler signaler = mock(InspectionAlarmSignaler.class);
        doThrow(new IllegalStateException("temporal down"))
                .when(signaler).signalAlarmDetected("TASK-1", "ALARM-1");
        AlarmOutbox row = new AlarmOutbox(
                "ALARM-1",
                AlarmOutboxKind.TEMPORAL_SIGNAL,
                objectMapper.writeValueAsString(
                        new AlarmTemporalSignalPayload("TASK-1", "ALARM-1")
                )
        );
        AlarmOutboxDispatcher dispatcher = dispatcher(repository, signaler, null, 2);

        dispatcher.tryDispatch(row);

        assertThat(row.getStatus()).isEqualTo(AlarmOutboxStatus.PENDING);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getAvailableAt()).isAfter(LocalDateTime.now().minusSeconds(1));
        org.springframework.test.util.ReflectionTestUtils.setField(
                row,
                "availableAt",
                LocalDateTime.now().minusSeconds(1)
        );

        dispatcher.tryDispatch(row);

        assertThat(row.getStatus()).isEqualTo(AlarmOutboxStatus.FAILED);
        assertThat(row.getAttempts()).isEqualTo(2);
        verify(signaler, org.mockito.Mockito.times(2))
                .signalAlarmDetected("TASK-1", "ALARM-1");
    }

    @Test
    void realtimeSuccessPublishes() throws Exception {
        AlarmOutboxRepository repository = mock(AlarmOutboxRepository.class);
        AlarmRealtimePublisher publisher = mock(AlarmRealtimePublisher.class);
        AlarmResponse alarm = sampleAlarm();
        AlarmOutbox row = new AlarmOutbox(
                "ALARM-1",
                AlarmOutboxKind.REALTIME,
                objectMapper.writeValueAsString(alarm)
        );

        dispatcher(repository, mock(InspectionAlarmSignaler.class), publisher)
                .tryDispatch(row);

        verify(publisher).publishCreated(alarm);
        assertThat(row.getStatus()).isEqualTo(AlarmOutboxStatus.SENT);
    }

    @Test
    void alreadySentIsNoOp() {
        AlarmOutboxRepository repository = mock(AlarmOutboxRepository.class);
        InspectionAlarmSignaler signaler = mock(InspectionAlarmSignaler.class);
        AlarmOutbox row = new AlarmOutbox(
                "ALARM-1",
                AlarmOutboxKind.TEMPORAL_SIGNAL,
                "{}"
        );
        row.markSent();

        dispatcher(repository, signaler, null).tryDispatch(row);

        verify(signaler, never()).signalAlarmDetected(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(repository, never()).save(row);
    }

    private AlarmOutboxDispatcher dispatcher(
            AlarmOutboxRepository repository,
            InspectionAlarmSignaler signaler,
            AlarmRealtimePublisher publisher) {
        return dispatcher(repository, signaler, publisher, 8);
    }

    private AlarmOutboxDispatcher dispatcher(
            AlarmOutboxRepository repository,
            InspectionAlarmSignaler signaler,
            AlarmRealtimePublisher publisher,
            int maxAttempts) {
        return new AlarmOutboxDispatcher(
                repository,
                provider(signaler),
                provider(publisher),
                objectMapper,
                mock(PlatformTransactionManager.class),
                50,
                maxAttempts
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
    private static <T> ObjectProvider<T> provider(T bean) {
        ObjectProvider<T> objectProvider = mock(ObjectProvider.class);
        when(objectProvider.getIfAvailable()).thenReturn(bean);
        doAnswer(invocation -> {
            if (bean != null) {
                Consumer<T> consumer = invocation.getArgument(0);
                consumer.accept(bean);
            }
            return null;
        }).when(objectProvider).ifAvailable(ArgumentMatchers.any());
        return objectProvider;
    }
}
