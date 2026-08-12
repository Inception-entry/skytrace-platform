package com.skytrace.backend.task.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 巡检任务状态机最小契约：CREATED → RUNNING → COMPLETED|CANCELLED|TIMED_OUT，
 * 终态不可再改业务字段（由 Service 层校验 isTerminal）。
 */
class InspectionTaskStateMachineTest {

    @Test
    void newTaskStartsAsCreated() {
        InspectionTask task = sample("TASK-SM-1");
        assertThat(task.getStatus()).isEqualTo("CREATED");
        assertThat(task.isTerminal()).isFalse();
    }

    @Test
    void canMoveCreatedToRunning() {
        InspectionTask task = sample("TASK-SM-2");
        task.changeStatus("RUNNING");
        assertThat(task.getStatus()).isEqualTo("RUNNING");
        assertThat(task.isTerminal()).isFalse();
    }

    @Test
    void completedIsTerminal() {
        InspectionTask task = sample("TASK-SM-3");
        task.changeStatus("RUNNING");
        task.changeStatus("COMPLETED");
        assertThat(task.getStatus()).isEqualTo("COMPLETED");
        assertThat(task.isTerminal()).isTrue();
    }

    @Test
    void cancelledIsTerminal() {
        InspectionTask task = sample("TASK-SM-4");
        task.changeStatus("RUNNING");
        task.changeStatus("CANCELLED");
        assertThat(task.getStatus()).isEqualTo("CANCELLED");
        assertThat(task.isTerminal()).isTrue();
    }

    @Test
    void changeStatusTouchesUpdatedAt() {
        InspectionTask task = sample("TASK-SM-5");
        LocalDateTime before = task.getUpdatedAt();
        task.changeStatus("RUNNING");
        assertThat(task.getUpdatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void timedOutIsTerminal() {
        InspectionTask task = sample("TASK-SM-6");
        task.changeStatus("RUNNING");
        task.changeStatus("TIMED_OUT");
        assertThat(task.getStatus()).isEqualTo("TIMED_OUT");
        assertThat(task.isTerminal()).isTrue();
    }

    private static InspectionTask sample(String code) {
        return new InspectionTask(
                code,
                "状态机样例",
                "UAV-001",
                "ROUTE-001",
                LocalDateTime.of(2026, 8, 12, 9, 0),
                LocalDateTime.of(2026, 8, 12, 10, 0)
        );
    }
}
