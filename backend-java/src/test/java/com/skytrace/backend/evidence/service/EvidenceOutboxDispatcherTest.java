package com.skytrace.backend.evidence.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytrace.backend.evidence.domain.EvidenceArchiveJob;
import com.skytrace.backend.evidence.domain.EvidenceArchiveJobStatus;
import com.skytrace.backend.evidence.domain.EvidenceOutbox;
import com.skytrace.backend.evidence.domain.EvidenceOutboxKind;
import com.skytrace.backend.evidence.domain.EvidenceOutboxStatus;
import com.skytrace.backend.evidence.dto.EvidenceMinioDeletePayload;
import com.skytrace.backend.evidence.repository.EvidenceArchiveJobRepository;
import com.skytrace.backend.evidence.repository.EvidenceOutboxRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvidenceOutboxDispatcherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void derivativeSuccessMarksSent() {
        EvidenceOutboxRepository repository = mock(EvidenceOutboxRepository.class);
        EvidenceDerivativeJobService derivative = mock(EvidenceDerivativeJobService.class);
        EvidenceOutbox row = new EvidenceOutbox(
                "EV-1",
                EvidenceOutboxKind.DERIVATIVE,
                "{\"code\":\"EV-1\"}"
        );

        dispatcher(repository, derivative, null, null, null).tryDispatch(row);

        verify(derivative).start("EV-1");
        verify(repository).save(row);
        assertThat(row.getStatus()).isEqualTo(EvidenceOutboxStatus.SENT);
        assertThat(row.getSentAt()).isNotNull();
    }

    @Test
    void derivativeFailureRetriesThenFails() {
        EvidenceOutboxRepository repository = mock(EvidenceOutboxRepository.class);
        EvidenceDerivativeJobService derivative = mock(EvidenceDerivativeJobService.class);
        doThrow(new IllegalStateException("temporal down")).when(derivative).start("EV-1");
        EvidenceOutbox row = new EvidenceOutbox(
                "EV-1",
                EvidenceOutboxKind.DERIVATIVE,
                "{\"code\":\"EV-1\"}"
        );
        EvidenceOutboxDispatcher dispatcher = dispatcher(
                repository,
                derivative,
                null,
                null,
                null,
                2
        );

        dispatcher.tryDispatch(row);

        assertThat(row.getStatus()).isEqualTo(EvidenceOutboxStatus.PENDING);
        assertThat(row.getAttempts()).isEqualTo(1);
        org.springframework.test.util.ReflectionTestUtils.setField(
                row,
                "availableAt",
                LocalDateTime.now().minusSeconds(1)
        );

        dispatcher.tryDispatch(row);

        assertThat(row.getStatus()).isEqualTo(EvidenceOutboxStatus.FAILED);
        assertThat(row.getAttempts()).isEqualTo(2);
        verify(derivative, org.mockito.Mockito.times(2)).start("EV-1");
    }

    @Test
    void archiveSuccessStartsWorkflow() {
        EvidenceOutboxRepository repository = mock(EvidenceOutboxRepository.class);
        EvidenceArchiveWorkflowStarter starter = mock(EvidenceArchiveWorkflowStarter.class);
        EvidenceOutbox row = new EvidenceOutbox(
                "AR-1",
                EvidenceOutboxKind.ARCHIVE,
                "{\"code\":\"AR-1\"}"
        );

        dispatcher(repository, mock(EvidenceDerivativeJobService.class), starter, null, null)
                .tryDispatch(row);

        verify(starter).start("AR-1");
        assertThat(row.getStatus()).isEqualTo(EvidenceOutboxStatus.SENT);
    }

    @Test
    void archiveExhaustedMarksJobFailed() {
        EvidenceOutboxRepository repository = mock(EvidenceOutboxRepository.class);
        EvidenceArchiveWorkflowStarter starter = mock(EvidenceArchiveWorkflowStarter.class);
        EvidenceArchiveJobRepository jobs = mock(EvidenceArchiveJobRepository.class);
        doThrow(new IllegalStateException("temporal down")).when(starter).start("AR-1");
        EvidenceArchiveJob job = new EvidenceArchiveJob();
        job.setJobCode("AR-1");
        job.setStatus(EvidenceArchiveJobStatus.PENDING);
        when(jobs.findByJobCode("AR-1")).thenReturn(Optional.of(job));
        EvidenceOutbox row = new EvidenceOutbox(
                "AR-1",
                EvidenceOutboxKind.ARCHIVE,
                "{\"code\":\"AR-1\"}"
        );
        EvidenceOutboxDispatcher dispatcher = dispatcher(
                repository,
                mock(EvidenceDerivativeJobService.class),
                starter,
                jobs,
                null,
                1
        );

        dispatcher.tryDispatch(row);

        assertThat(row.getStatus()).isEqualTo(EvidenceOutboxStatus.FAILED);
        assertThat(job.getStatus()).isEqualTo(EvidenceArchiveJobStatus.FAILED);
        assertThat(job.getErrorMessage()).contains("temporal down");
        verify(jobs).save(job);
    }

    @Test
    void minioDeleteRemovesObject() throws Exception {
        EvidenceOutboxRepository repository = mock(EvidenceOutboxRepository.class);
        EvidenceStorageService storage = mock(EvidenceStorageService.class);
        EvidenceOutbox row = new EvidenceOutbox(
                "tasks/a.jpg",
                EvidenceOutboxKind.MINIO_DELETE,
                objectMapper.writeValueAsString(
                        new EvidenceMinioDeletePayload("skytrace-evidence", "tasks/a.jpg")
                )
        );

        dispatcher(repository, mock(EvidenceDerivativeJobService.class), null, null, storage)
                .tryDispatch(row);

        verify(storage).removeEvidenceObject("skytrace-evidence", "tasks/a.jpg");
        assertThat(row.getStatus()).isEqualTo(EvidenceOutboxStatus.SENT);
    }

    @Test
    void alreadySentIsNoOp() {
        EvidenceOutboxRepository repository = mock(EvidenceOutboxRepository.class);
        EvidenceDerivativeJobService derivative = mock(EvidenceDerivativeJobService.class);
        EvidenceOutbox row = new EvidenceOutbox(
                "EV-1",
                EvidenceOutboxKind.DERIVATIVE,
                "{}"
        );
        row.markSent();

        dispatcher(repository, derivative, null, null, null).tryDispatch(row);

        verify(derivative, never()).start(org.mockito.ArgumentMatchers.any());
        verify(repository, never()).save(row);
    }

    private EvidenceOutboxDispatcher dispatcher(
            EvidenceOutboxRepository repository,
            EvidenceDerivativeJobService derivative,
            EvidenceArchiveWorkflowStarter starter,
            EvidenceArchiveJobRepository jobs,
            EvidenceStorageService storage) {
        return dispatcher(repository, derivative, starter, jobs, storage, 8);
    }

    private EvidenceOutboxDispatcher dispatcher(
            EvidenceOutboxRepository repository,
            EvidenceDerivativeJobService derivative,
            EvidenceArchiveWorkflowStarter starter,
            EvidenceArchiveJobRepository jobs,
            EvidenceStorageService storage,
            int maxAttempts) {
        return new EvidenceOutboxDispatcher(
                repository,
                derivative,
                provider(starter),
                jobs == null ? mock(EvidenceArchiveJobRepository.class) : jobs,
                provider(storage),
                objectMapper,
                mock(PlatformTransactionManager.class),
                50,
                maxAttempts
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
