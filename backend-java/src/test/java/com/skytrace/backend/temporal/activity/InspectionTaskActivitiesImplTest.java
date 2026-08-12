package com.skytrace.backend.temporal.activity;

import com.skytrace.backend.task.domain.InspectionTask;
import com.skytrace.backend.task.repository.InspectionTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Temporal Activity 驱动的任务状态迁移与启动前校验。
 */
class InspectionTaskActivitiesImplTest {

    private final InspectionTaskRepository repository =
            mock(InspectionTaskRepository.class);
    private InspectionTaskActivitiesImpl activities;

    @BeforeEach
    void setUp() {
        activities = new InspectionTaskActivitiesImpl(repository);
    }

    @Test
    void createTaskIfAbsentSkipsExisting() {
        when(repository.existsByTaskCode("TASK-1")).thenReturn(true);

        activities.createTaskIfAbsent("TASK-1");

        verify(repository, never()).save(any());
    }

    @Test
    void createTaskIfAbsentPersistsNewTask() {
        when(repository.existsByTaskCode("TASK-NEW")).thenReturn(false);
        when(repository.save(any(InspectionTask.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        activities.createTaskIfAbsent("TASK-NEW");

        verify(repository).save(any(InspectionTask.class));
    }

    @Test
    void updateStatusWritesRunning() {
        InspectionTask task = new InspectionTask(
                "TASK-2",
                "样例",
                "UAV-001",
                LocalDateTime.of(2026, 8, 12, 9, 0),
                LocalDateTime.of(2026, 8, 12, 10, 0)
        );
        when(repository.findByTaskCode("TASK-2")).thenReturn(Optional.of(task));

        activities.updateStatus("TASK-2", "RUNNING");

        assertThat(task.getStatus()).isEqualTo("RUNNING");
    }

    @Test
    void updateStatusFailsWhenMissing() {
        when(repository.findByTaskCode("GONE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> activities.updateStatus("GONE", "RUNNING"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    void assertStartableRejectsTerminalTask() {
        InspectionTask task = new InspectionTask(
                "TASK-E",
                "已完成",
                "UAV-001",
                LocalDateTime.of(2026, 8, 12, 9, 0),
                LocalDateTime.of(2026, 8, 12, 10, 0)
        );
        task.changeStatus("COMPLETED");
        when(repository.findByTaskCode("TASK-E")).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> activities.assertStartable("TASK-E"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("终态");
    }

    @Test
    void assertStartableAllowsCreatedTask() {
        InspectionTask task = new InspectionTask(
                "TASK-F",
                "待启动",
                "UAV-001",
                LocalDateTime.of(2026, 8, 12, 9, 0),
                LocalDateTime.of(2026, 8, 12, 10, 0)
        );
        when(repository.findByTaskCode("TASK-F")).thenReturn(Optional.of(task));

        activities.assertStartable("TASK-F");
    }
}
