package com.skytrace.backend.telemetry.service;

import com.skytrace.backend.task.repository.InspectionTaskRepository;
import com.skytrace.backend.telemetry.domain.DeviceTelemetryPoint;
import com.skytrace.backend.telemetry.dto.DeviceTelemetryPointResponse;
import com.skytrace.backend.telemetry.repository.DeviceTelemetryPointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 任务级飞行遥测历史。只有当设备存在一个 RUNNING 任务时才落库，
 * 用于事后在地图上回放这次任务飞过的轨迹；不落历史等同于该次遥测无归属任务。
 */
@Service
@Transactional
public class DeviceTelemetryHistoryService {

    private static final Logger log =
            LoggerFactory.getLogger(DeviceTelemetryHistoryService.class);
    private static final String RUNNING_STATUS = "RUNNING";

    private final DeviceTelemetryPointRepository telemetryRepository;
    private final InspectionTaskRepository taskRepository;

    public DeviceTelemetryHistoryService(
            DeviceTelemetryPointRepository telemetryRepository,
            InspectionTaskRepository taskRepository) {
        this.telemetryRepository = telemetryRepository;
        this.taskRepository = taskRepository;
    }

    public void recordIfTaskRunning(
            String deviceCode,
            double latitude,
            double longitude,
            Double altitude,
            Double heading,
            String source,
            String ts) {
        taskRepository
                .findFirstByDeviceCodeAndStatusOrderByUpdatedAtDesc(
                        deviceCode, RUNNING_STATUS)
                .ifPresent(task -> {
                    try {
                        DeviceTelemetryPoint point = new DeviceTelemetryPoint(
                                deviceCode,
                                task.getTaskCode(),
                                BigDecimal.valueOf(latitude),
                                BigDecimal.valueOf(longitude),
                                altitude == null ? null : BigDecimal.valueOf(altitude),
                                heading == null ? null : BigDecimal.valueOf(heading),
                                source,
                                parseRecordedAt(ts)
                        );
                        telemetryRepository.save(point);
                    } catch (Exception ex) {
                        log.warn("写入遥测历史失败: {}", ex.getMessage());
                    }
                });
    }

    @Transactional(readOnly = true)
    public List<DeviceTelemetryPointResponse> findTrackByTaskCode(String taskCode) {
        return telemetryRepository.findByTaskCodeOrderByRecordedAtAsc(taskCode)
                .stream()
                .map(point -> new DeviceTelemetryPointResponse(
                        point.getLatitude(),
                        point.getLongitude(),
                        point.getAltitude(),
                        point.getHeading(),
                        point.getRecordedAt()
                ))
                .toList();
    }

    private static LocalDateTime parseRecordedAt(String ts) {
        if (ts == null || ts.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return java.time.Instant.parse(ts)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (DateTimeParseException ex) {
            return LocalDateTime.now();
        }
    }
}
