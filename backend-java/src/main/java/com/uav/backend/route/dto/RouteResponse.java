package com.uav.backend.route.dto;

import java.time.LocalDateTime;

public record RouteResponse(
        String routeCode,
        String routeName,
        String description,
        String waypointsJson,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
