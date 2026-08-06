package com.skytrace.backend.task.repository;

import com.skytrace.backend.task.domain.InspectionTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InspectionTaskRepository
        extends JpaRepository<InspectionTask, Long> {

    Optional<InspectionTask> findByTaskCode(String taskCode);

    boolean existsByTaskCode(String taskCode);

    long countByStatus(String status);

    boolean existsByDeviceCode(String deviceCode);
}
