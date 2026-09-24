package com.skytrace.backend.task.service;

import com.skytrace.backend.cache.DevicePresenceService;
import com.skytrace.backend.common.ConflictException;
import com.skytrace.backend.common.TextEncodingFix;
import com.skytrace.backend.device.domain.Device;
import com.skytrace.backend.device.repository.DeviceRepository;
import com.skytrace.backend.route.domain.InspectionRoute;
import com.skytrace.backend.route.repository.InspectionRouteRepository;
import com.skytrace.backend.task.domain.InspectionTask;
import com.skytrace.backend.task.dto.CreateInspectionTaskRequest;
import com.skytrace.backend.task.dto.InspectionTaskAnalysisContext;
import com.skytrace.backend.task.dto.InspectionTaskPageResponse;
import com.skytrace.backend.task.dto.InspectionTaskResponse;
import com.skytrace.backend.task.dto.UpdateInspectionTaskRequest;
import com.skytrace.backend.task.repository.InspectionTaskRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class InspectionTaskService {

    private final InspectionTaskRepository repository;
    private final DeviceRepository deviceRepository;
    private final InspectionRouteRepository routeRepository;
    private final ObjectProvider<DevicePresenceService> presenceService;

    public InspectionTaskService(
            InspectionTaskRepository repository,
            DeviceRepository deviceRepository,
            InspectionRouteRepository routeRepository,
            ObjectProvider<DevicePresenceService> presenceService) {
        this.repository = repository;
        this.deviceRepository = deviceRepository;
        this.routeRepository = routeRepository;
        this.presenceService = presenceService;
    }

    public List<InspectionTaskResponse> findAll() {
        return toResponses(repository.findAll(
                Sort.by(Sort.Direction.DESC, "createdAt")
        ));
    }

    public InspectionTaskPageResponse findPage(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        var result = repository.findAll(PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
        ));
        return new InspectionTaskPageResponse(
                toResponses(result.getContent()),
                safePage,
                safeSize,
                result.getTotalElements()
        );
    }

    public InspectionTaskResponse findByTaskCode(
            String taskCode) {
        InspectionTask task = getRequiredTask(taskCode);
        return respond(task);
    }

    @Transactional
    public InspectionTaskResponse create(
            CreateInspectionTaskRequest request) {
        validatePlanTime(
                request.planStartTime(),
                request.planEndTime()
        );
        requireExistingDevice(request.deviceCode());
        String routeCode = normalizeOptionalCode(request.routeCode());
        requireExistingRouteIfPresent(routeCode);
        if (repository.existsByTaskCode(request.taskCode())) {
            throw new ConflictException(
                    "任务编号已存在：" + request.taskCode()
            );
        }

        InspectionTask task = new InspectionTask(
                request.taskCode(),
                request.taskName().trim(),
                request.deviceCode().trim(),
                routeCode,
                request.planStartTime(),
                request.planEndTime()
        );
        return respond(repository.save(task));
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
        String routeCode = normalizeOptionalCode(request.routeCode());
        requireExistingRouteIfPresent(routeCode);
        InspectionTask task = getRequiredTask(taskCode);
        if (task.isTerminal()) {
            throw new ConflictException(
                    "已完成或已取消的任务不能修改：" + taskCode
            );
        }

        task.updateDetails(
                request.taskName().trim(),
                request.deviceCode().trim(),
                routeCode,
                request.planStartTime(),
                request.planEndTime()
        );
        return respond(task);
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

    private void requireExistingRouteIfPresent(String routeCode) {
        if (routeCode == null) {
            return;
        }
        if (!routeRepository.existsByRouteCode(routeCode)) {
            throw new NoSuchElementException("航线不存在：" + routeCode);
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

    private static String normalizeOptionalCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private InspectionTaskResponse respond(InspectionTask task) {
        return toResponses(List.of(task)).getFirst();
    }

    private List<InspectionTaskResponse> toResponses(List<InspectionTask> tasks) {
        Set<String> deviceCodes = tasks.stream()
                .map(InspectionTask::getDeviceCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());
        Set<String> routeCodes = tasks.stream()
                .map(InspectionTask::getRouteCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());
        Map<String, Device> devices = deviceCodes.isEmpty()
                ? Map.of()
                : deviceRepository.findByDeviceCodeIn(deviceCodes).stream()
                        .collect(Collectors.toMap(
                                Device::getDeviceCode,
                                Function.identity(),
                                (left, right) -> left,
                                HashMap::new
                        ));
        Map<String, InspectionRoute> routes = routeCodes.isEmpty()
                ? Map.of()
                : routeRepository.findByRouteCodeIn(routeCodes).stream()
                        .collect(Collectors.toMap(
                                InspectionRoute::getRouteCode,
                                Function.identity(),
                                (left, right) -> left,
                                HashMap::new
                        ));
        Set<String> online = onlineDeviceCodes();
        return tasks.stream()
                .map(task -> toResponse(task, online, devices, routes))
                .toList();
    }

    private InspectionTaskResponse toResponse(
            InspectionTask task,
            Set<String> online,
            Map<String, Device> devices,
            Map<String, InspectionRoute> routes) {
        String deviceCode = task.getDeviceCode();
        Optional<Device> device = deviceCode == null || deviceCode.isBlank()
                ? Optional.empty()
                : Optional.ofNullable(devices.get(deviceCode));
        String deviceName = TextEncodingFix.repairMojibake(
                device.map(Device::getDeviceName).orElse(null)
        );
        String deviceStatus = device.isEmpty()
                ? null
                : (online.contains(deviceCode) ? "ONLINE" : "OFFLINE");

        String routeCode = task.getRouteCode();
        Optional<InspectionRoute> route = routeCode == null || routeCode.isBlank()
                ? Optional.empty()
                : Optional.ofNullable(routes.get(routeCode));
        String routeName = TextEncodingFix.repairMojibake(
                route.map(InspectionRoute::getRouteName).orElse(null)
        );

        return new InspectionTaskResponse(
                task.getTaskCode(),
                task.getTaskName(),
                deviceCode,
                deviceName,
                deviceStatus,
                routeCode,
                routeName,
                task.getStatus(),
                task.getPlanStartTime(),
                task.getPlanEndTime(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
