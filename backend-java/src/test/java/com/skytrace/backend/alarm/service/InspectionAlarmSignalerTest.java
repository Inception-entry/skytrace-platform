package com.skytrace.backend.alarm.service;

import com.skytrace.backend.temporal.workflow.InspectionWorkflow;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InspectionAlarmSignalerTest {

    @Test
    void missingWorkflowIsSuccess() {
        WorkflowClient client = mock(WorkflowClient.class);
        InspectionWorkflow workflow = mock(InspectionWorkflow.class);
        when(client.newWorkflowStub(InspectionWorkflow.class, "inspection-TASK-1"))
                .thenReturn(workflow);
        doThrow(new WorkflowNotFoundException(
                WorkflowExecution.newBuilder().setWorkflowId("inspection-TASK-1").build(),
                "InspectionWorkflow",
                null
        )).when(workflow).alarmDetected("ALARM-1");

        new InspectionAlarmSignaler(client).signalAlarmDetected("TASK-1", "ALARM-1");

        verify(workflow).alarmDetected("ALARM-1");
    }

    @Test
    void otherFailuresRethrow() {
        WorkflowClient client = mock(WorkflowClient.class);
        InspectionWorkflow workflow = mock(InspectionWorkflow.class);
        when(client.newWorkflowStub(InspectionWorkflow.class, "inspection-TASK-1"))
                .thenReturn(workflow);
        doThrow(new IllegalStateException("temporal down"))
                .when(workflow)
                .alarmDetected("ALARM-1");

        assertThatThrownBy(() ->
                new InspectionAlarmSignaler(client)
                        .signalAlarmDetected("TASK-1", "ALARM-1")
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("temporal down");
    }
}
