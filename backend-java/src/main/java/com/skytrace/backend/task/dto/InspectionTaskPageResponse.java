package com.skytrace.backend.task.dto;

import java.util.List;

public record InspectionTaskPageResponse(
        List<InspectionTaskResponse> content,
        int page,
        int size,
        long total
) {
}
