package com.skytrace.backend.route.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateRouteRequest(
        @NotBlank(message = "航线名称不能为空")
        @Size(max = 128, message = "航线名称不能超过 128 个字符")
        String routeName,

        @Size(max = 512, message = "航线描述不能超过 512 个字符")
        String description,

        String waypointsJson
) {
}
