package com.uav.backend.route.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRouteRequest(
        @NotBlank(message = "航线编号不能为空")
        @Size(max = 64, message = "航线编号不能超过 64 个字符")
        @Pattern(
                regexp = "^[A-Za-z0-9_-]+$",
                message = "航线编号只能包含字母、数字、下划线和中划线"
        )
        String routeCode,

        @NotBlank(message = "航线名称不能为空")
        @Size(max = 128, message = "航线名称不能超过 128 个字符")
        String routeName,

        @Size(max = 512, message = "航线描述不能超过 512 个字符")
        String description,

        String waypointsJson
) {
}
