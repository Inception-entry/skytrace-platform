package com.skytrace.backend.device;

import com.skytrace.backend.common.ApiResponse;
import com.skytrace.backend.device.dto.CreateDeviceRequest;
import com.skytrace.backend.device.dto.DeviceResponse;
import com.skytrace.backend.device.dto.UpdateDeviceRequest;
import com.skytrace.backend.device.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.DeleteMapping;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    public ApiResponse<List<DeviceResponse>> list() {
        return ApiResponse.ok(deviceService.findAll());
    }

    @GetMapping("/{deviceCode}")
    public ApiResponse<DeviceResponse> detail(
            @PathVariable String deviceCode) {
        return ApiResponse.ok(deviceService.findByDeviceCode(deviceCode));
    }

    @PostMapping
    public ApiResponse<DeviceResponse> create(
            @Valid @RequestBody CreateDeviceRequest request) {
        return ApiResponse.ok(deviceService.create(request));
    }

    @PutMapping("/{deviceCode}")
    public ApiResponse<DeviceResponse> update(
            @PathVariable String deviceCode,
            @Valid @RequestBody UpdateDeviceRequest request) {
        return ApiResponse.ok(deviceService.update(deviceCode, request));
    }

    @PostMapping("/{deviceCode}/heartbeat")
    public ApiResponse<Map<String, Object>> heartbeat(
            @PathVariable String deviceCode) {
        return ApiResponse.ok(deviceService.heartbeat(deviceCode));
    }

    @DeleteMapping("/{deviceCode}")
    public ApiResponse<Void> delete(@PathVariable String deviceCode) {
        deviceService.delete(deviceCode);
        return ApiResponse.ok(null);
    }
}