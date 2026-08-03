package com.skytrace.backend.temporal.activity;

import com.skytrace.backend.task.domain.InspectionTask;
import com.skytrace.backend.task.repository.InspectionTaskRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("inspectionTaskActivities")
public class InspectionTaskActivitiesImpl
        implements InspectionTaskActivities {

    private final InspectionTaskRepository repository;

    public InspectionTaskActivitiesImpl(
            InspectionTaskRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void createTaskIfAbsent(String taskCode) {
        if (repository.existsByTaskCode(taskCode)) {
            return;
        }

        repository.save(new InspectionTask(taskCode));
    }

    @Override
    @Transactional
    public void updateStatus(String taskCode, String status) {
        InspectionTask task = repository.findByTaskCode(taskCode)
                .orElseThrow(() -> new IllegalStateException(
                        "巡检任务不存在：" + taskCode
                ));

        task.changeStatus(status);
    }
}