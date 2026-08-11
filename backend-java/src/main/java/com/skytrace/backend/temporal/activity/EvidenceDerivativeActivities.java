package com.skytrace.backend.temporal.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface EvidenceDerivativeActivities {
    @ActivityMethod
    void generateDerivatives(String evidenceCode);
}
