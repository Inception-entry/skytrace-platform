package com.uav.backend.device.service;

import com.uav.backend.cache.DevicePresenceService;
import com.uav.backend.common.ConflictException;
import com.uav.backend.device.domain.Device;
import com.uav.backend.device.dto.CreateDeviceRequest;
import com.uav.backend.device.dto.DeviceResponse;
import com.uav.backend.device.dto.UpdateDeviceRequest;
import com.uav.backend.device.repository.DeviceRepository;
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

    public DeviceService(
            DeviceRepository repository,
            ObjectProvider<DevicePresenceService> presenceService) {
        this.repository = repository;
        this.presenceService = presenceService;
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
                device.getDeviceName(),
                device.getDeviceType(),
                runtimeStatus,
                device.getCreatedAt(),
                device.getUpdatedAt()
        );
    }
}
