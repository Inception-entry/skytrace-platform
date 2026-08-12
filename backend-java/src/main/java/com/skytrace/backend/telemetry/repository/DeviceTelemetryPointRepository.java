package com.skytrace.backend.telemetry.repository;

import com.skytrace.backend.telemetry.domain.DeviceTelemetryPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceTelemetryPointRepository
        extends JpaRepository<DeviceTelemetryPoint, Long> {

    List<DeviceTelemetryPoint> findByTaskCodeOrderByRecordedAtAsc(String taskCode);

    long countByTaskCode(String taskCode);
}
