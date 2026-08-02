package com.uav.backend.task.service;

import com.uav.backend.cache.DevicePresenceService;
import com.uav.backend.common.ConflictException;
import com.uav.backend.device.domain.Device;
import com.uav.backend.device.repository.DeviceRepository;
import com.uav.backend.task.domain.InspectionTask;
import com.uav.backend.task.dto.CreateInspectionTaskRequest;
import com.uav.backend.task.dto.InspectionTaskAnalysisContext;
import com.uav.backend.task.dto.InspectionTaskResponse;
import com.uav.backend.task.dto.UpdateInspectionTaskRequest;
import com.uav.backend.task.repository.InspectionTaskRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class InspectionTaskService {

    private final InspectionTaskRepository repository;
    private final DeviceRepository deviceRepository;
    private final ObjectProvider<DevicePresenceService> presenceService;

    public InspectionTaskService(
            InspectionTaskRepository repository,
            DeviceRepository deviceRepository,
            ObjectProvider<DevicePresenceService> presenceService) {
        this.repository = repository;
        this.deviceRepository = deviceRepository;
        this.presenceService = presenceService;
    }

    public List<InspectionTaskResponse> findAll() {
        Set<String> online = onlineDeviceCodes();
        return repository.findAll(
                        Sort.by(
                                Sort.Direction.DESC,
                                "createdAt"
                        )
                )
                .stream()
                .map(task -> toResponse(task, online))
                .toList();
    }

    public InspectionTaskResponse findByTaskCode(
            String taskCode) {
        InspectionTask task = getRequiredTask(taskCode);
        return toResponse(task, onlineDeviceCodes());
    }

    @Transactional
    public InspectionTaskResponse create(
            CreateInspectionTaskRequest request) {
        validatePlanTime(
                request.planStartTime(),
                request.planEndTime()
        );
        requireExistingDevice(request.deviceCode());
        if (repository.existsByTaskCode(request.taskCode())) {
            throw new ConflictException(
                    "任务编号已存在：" + request.taskCode()
            );
        }

        InspectionTask task = new InspectionTask(
                request.taskCode(),
                request.taskName().trim(),
                request.deviceCode().trim(),
                request.planStartTime(),
                request.planEndTime()
        );
        return toResponse(
                repository.save(task),
                onlineDeviceCodes()
        );
    }

    @Transactional
    public InspectionTaskResponse update(
            String taskCode,
            UpdateInspectionTaskRequest request) {
        validatePlanTime(
                request.planStartTime(),
                request.planEndTime()
        );
        requireExistingDevice(request.deviceCode());
        InspectionTask task = getRequiredTask(taskCode);
        if (task.isTerminal()) {
            throw new ConflictException(
                    "已完成或已取消的任务不能修改：" + taskCode
            );
        }

        task.updateDetails(
                request.taskName().trim(),
                request.deviceCode().trim(),
                request.planStartTime(),
                request.planEndTime()
        );
        return toResponse(task, onlineDeviceCodes());
    }

    public InspectionTaskAnalysisContext findAnalysisContext(
            String taskCode) {
        InspectionTask task = getRequiredTask(taskCode);

        return new InspectionTaskAnalysisContext(
                task.getTaskCode(),
                task.getTaskName(),
                task.getDeviceCode(),
                task.getStatus(),
                task.getPlanStartTime(),
                task.getPlanEndTime(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    private void requireExistingDevice(String deviceCode) {
        String code = deviceCode == null ? "" : deviceCode.trim();
        if (!deviceRepository.existsByDeviceCode(code)) {
            throw new NoSuchElementException("设备不存在：" + code);
        }
    }

    private InspectionTask getRequiredTask(String taskCode) {
        return repository.findByTaskCode(taskCode)
                .orElseThrow(() -> new NoSuchElementException(
                        "巡检任务不存在：" + taskCode
                ));
    }

    private void validatePlanTime(
            java.time.LocalDateTime planStartTime,
            java.time.LocalDateTime planEndTime) {
        if (!planEndTime.isAfter(planStartTime)) {
            throw new IllegalArgumentException(
                    "计划结束时间必须晚于计划开始时间"
            );
        }
    }

    private Set<String> onlineDeviceCodes() {
        DevicePresenceService presence = presenceService.getIfAvailable();
        if (presence == null) {
            return Set.of();
        }
        return presence.onlineDeviceCodes();
    }

    private InspectionTaskResponse toResponse(
            InspectionTask task,
            Set<String> online) {
        String deviceCode = task.getDeviceCode();
        Optional<Device> device = deviceCode == null || deviceCode.isBlank()
                ? Optional.empty()
                : deviceRepository.findByDeviceCode(deviceCode);
        String deviceName = device.map(Device::getDeviceName).orElse(null);
        String deviceStatus = device.isEmpty()
                ? null
                : (online.contains(deviceCode) ? "ONLINE" : "OFFLINE");

        return new InspectionTaskResponse(
                task.getTaskCode(),
                task.getTaskName(),
                deviceCode,
                deviceName,
                deviceStatus,
                task.getStatus(),
                task.getPlanStartTime(),
                task.getPlanEndTime(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
