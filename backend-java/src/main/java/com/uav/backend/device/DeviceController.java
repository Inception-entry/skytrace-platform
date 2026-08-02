package com.uav.backend.device;

import com.uav.backend.cache.DevicePresenceService;
import com.uav.backend.common.ApiResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/devices")
public class DeviceController {
    private static final List<Map<String, String>> CATALOG = List.of(
            Map.of(
                    "deviceCode", "UAV-001",
                    "deviceName", "一号无人机"
            ),
            Map.of(
                    "deviceCode", "CAMERA-001",
                    "deviceName", "一号固定摄像头"
            )
    );

    private final ObjectProvider<DevicePresenceService> presenceService;

    public DeviceController(
            ObjectProvider<DevicePresenceService> presenceService) {
        this.presenceService = presenceService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        DevicePresenceService presence = presenceService.getIfAvailable();
        Set<String> online = presence == null
                ? Set.of()
                : presence.onlineDeviceCodes();
        List<Map<String, Object>> devices = CATALOG.stream()
                .map(item -> {
                    String code = item.get("deviceCode");
                    boolean isOnline = online.contains(code);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("deviceCode", code);
                    row.put("deviceName", item.get("deviceName"));
                    row.put("status", isOnline ? "ONLINE" : "OFFLINE");
                    return row;
                })
                .toList();
        return ApiResponse.ok(devices);
    }

    @PostMapping("/{deviceCode}/heartbeat")
    public ApiResponse<Map<String, Object>> heartbeat(
            @PathVariable String deviceCode) {
        DevicePresenceService presence = presenceService.getIfAvailable();
        if (presence == null) {
            return ApiResponse.ok(Map.of(
                    "deviceCode", deviceCode,
                    "status", "UNKNOWN",
                    "presence", "disabled"
            ));
        }
        presence.heartbeat(deviceCode);
        return ApiResponse.ok(Map.of(
                "deviceCode", deviceCode,
                "status", "ONLINE",
                "presence", "ok"
        ));
    }
}
