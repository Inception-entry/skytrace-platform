package com.skytrace.backend.device.service;

import com.skytrace.backend.cache.DevicePresenceService;
import com.skytrace.backend.common.ConflictException;
import com.skytrace.backend.common.TextEncodingFix;
import com.skytrace.backend.device.domain.Device;
import com.skytrace.backend.device.dto.CreateDeviceRequest;
import com.skytrace.backend.device.dto.DeviceResponse;
import com.skytrace.backend.device.dto.UpdateDeviceRequest;
import com.skytrace.backend.device.repository.DeviceRepository;
import com.skytrace.backend.alarm.repository.AlarmEventRepository;
import com.skytrace.backend.task.repository.InspectionTaskRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class DeviceService {

    private final DeviceRepository repository;
    private final ObjectProvider<DevicePresenceService> presenceService;
    private final InspectionTaskRepository taskRepository;
    private final AlarmEventRepository alarmRepository;

    public DeviceService(
            DeviceRepository repository,
            ObjectProvider<DevicePresenceService> presenceService,
            InspectionTaskRepository taskRepository,
            AlarmEventRepository alarmRepository) {
        this.repository = repository;
        this.presenceService = presenceService;
        this.taskRepository = taskRepository;
        this.alarmRepository = alarmRepository;
    }

    public List<DeviceResponse> findAll() {
        Set<String> online = onlineDeviceCodes();
        return repository.findAll(
                        Sort.by(Sort.Direction.ASC, "deviceCode")
                )
                .stream()
                .map(device -> toResponse(device, online))
                .toList();
    }

    public DeviceResponse findByDeviceCode(String deviceCode) {
        Device device = getRequiredDevice(deviceCode);
        return toResponse(device, onlineDeviceCodes());
    }

    @Transactional
    public DeviceResponse create(CreateDeviceRequest request) {
        String code = request.deviceCode().trim();
        if (repository.existsByDeviceCode(code)) {
            throw new ConflictException("设备编号已存在：" + code);
        }

        Device device = new Device(
                code,
                request.deviceName().trim(),
                request.deviceType().trim().toUpperCase()
        );
        return toResponse(repository.save(device), onlineDeviceCodes());
    }

    @Transactional
    public DeviceResponse update(
            String deviceCode,
            UpdateDeviceRequest request) {
        Device device = getRequiredDevice(deviceCode);
        device.updateDetails(
                request.deviceName().trim(),
                request.deviceType().trim().toUpperCase()
        );
        return toResponse(device, onlineDeviceCodes());
    }

    public Map<String, Object> heartbeat(String deviceCode) {
        getRequiredDevice(deviceCode);
        DevicePresenceService presence = presenceService.getIfAvailable();
        if (presence == null) {
            return Map.of(
                    "deviceCode", deviceCode.trim(),
                    "status", "UNKNOWN",
                    "presence", "disabled"
            );
        }
        presence.heartbeat(deviceCode);
        return Map.of(
                "deviceCode", deviceCode.trim(),
                "status", "ONLINE",
                "presence", "ok"
        );
    }

    @Transactional
    public void delete(String deviceCode) {
        Device device = getRequiredDevice(deviceCode); // 已有：不存在 → NoSuchElementException → 404
        String code = device.getDeviceCode();

        if (taskRepository.existsByDeviceCode(code) || alarmRepository.existsByDeviceCode(code)) {
             throw new ConflictException(
                "设备仍被任务或告警引用，无法删除：" + code
            );
        }

        repository.delete(device);
        DevicePresenceService presence = presenceService.getIfAvailable();

        if (presence != null) {
            presence.clear(code);
        }
    }

    private Device getRequiredDevice(String deviceCode) {
        String code = deviceCode == null ? "" : deviceCode.trim();
        return repository.findByDeviceCode(code)
                .orElseThrow(() -> new NoSuchElementException(
                        "设备不存在：" + code
                ));
    }

    private Set<String> onlineDeviceCodes() {
        DevicePresenceService presence = presenceService.getIfAvailable();
        if (presence == null) {
            return Set.of();
        }
        return presence.onlineDeviceCodes();
    }

    private DeviceResponse toResponse(
            Device device,
            Set<String> online) {
        String runtimeStatus = online.contains(device.getDeviceCode())
                ? "ONLINE"
                : "OFFLINE";
        return new DeviceResponse(
                device.getDeviceCode(),
                TextEncodingFix.repairMojibake(device.getDeviceName()),
                device.getDeviceType(),
                runtimeStatus,
                device.getCreatedAt(),
                device.getUpdatedAt()
        );
    }
}
