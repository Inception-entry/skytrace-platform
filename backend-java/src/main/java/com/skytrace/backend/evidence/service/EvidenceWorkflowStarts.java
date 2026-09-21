package com.skytrace.backend.evidence.service;

import io.temporal.client.WorkflowExecutionAlreadyStarted;

final class EvidenceWorkflowStarts {

    private EvidenceWorkflowStarts() {
    }

    static boolean alreadyStarted(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof WorkflowExecutionAlreadyStarted) {
                return true;
            }
        }
        return false;
    }
}
