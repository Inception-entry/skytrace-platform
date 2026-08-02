package com.uav.backend.route;

import com.uav.backend.common.ApiResponse;
import com.uav.backend.route.dto.CreateRouteRequest;
import com.uav.backend.route.dto.RouteResponse;
import com.uav.backend.route.dto.UpdateRouteRequest;
import com.uav.backend.route.service.InspectionRouteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/routes")
public class InspectionRouteController {

    private final InspectionRouteService routeService;

    public InspectionRouteController(InspectionRouteService routeService) {
        this.routeService = routeService;
    }

    @GetMapping
    public ApiResponse<List<RouteResponse>> list() {
        return ApiResponse.ok(routeService.findAll());
    }

    @GetMapping("/{routeCode}")
    public ApiResponse<RouteResponse> detail(@PathVariable String routeCode) {
        return ApiResponse.ok(routeService.findByRouteCode(routeCode));
    }

    @PostMapping
    public ApiResponse<RouteResponse> create(
            @Valid @RequestBody CreateRouteRequest request) {
        return ApiResponse.ok(routeService.create(request));
    }

    @PutMapping("/{routeCode}")
    public ApiResponse<RouteResponse> update(
            @PathVariable String routeCode,
            @Valid @RequestBody UpdateRouteRequest request) {
        return ApiResponse.ok(routeService.update(routeCode, request));
    }
}
