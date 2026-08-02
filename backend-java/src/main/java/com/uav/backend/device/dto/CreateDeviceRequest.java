package com.uav.backend.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDeviceRequest(
        @NotBlank(message = "设备编号不能为空")
        @Size(max = 64, message = "设备编号不能超过 64 个字符")
        @Pattern(
                regexp = "^[A-Za-z0-9_-]+$",
                message = "设备编号只能包含字母、数字、下划线和中划线"
        )
        String deviceCode,

        @NotBlank(message = "设备名称不能为空")
        @Size(max = 128, message = "设备名称不能超过 128 个字符")
        String deviceName,

        @NotBlank(message = "设备类型不能为空")
        @Size(max = 64, message = "设备类型不能超过 64 个字符")
        @Pattern(
                regexp = "^[A-Za-z0-9_-]+$",
                message = "设备类型只能包含字母、数字、下划线和中划线"
        )
        String deviceType
) {
}
