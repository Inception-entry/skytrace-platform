package com.skytrace.backend.route.service;

import com.skytrace.backend.common.ConflictException;
import com.skytrace.backend.route.domain.InspectionRoute;
import com.skytrace.backend.route.dto.CreateRouteRequest;
import com.skytrace.backend.route.dto.RouteResponse;
import com.skytrace.backend.route.dto.UpdateRouteRequest;
import com.skytrace.backend.route.repository.InspectionRouteRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class InspectionRouteService {

    private final InspectionRouteRepository repository;

    public InspectionRouteService(InspectionRouteRepository repository) {
        this.repository = repository;
    }

    public List<RouteResponse> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "routeCode"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public RouteResponse findByRouteCode(String routeCode) {
        return toResponse(getRequired(routeCode));
    }

    @Transactional
    public RouteResponse create(CreateRouteRequest request) {
        String code = request.routeCode().trim();
        if (repository.existsByRouteCode(code)) {
            throw new ConflictException("航线编号已存在：" + code);
        }
        InspectionRoute route = new InspectionRoute(
                code,
                request.routeName().trim(),
                blankToNull(request.description()),
                blankToNull(request.waypointsJson())
        );
        return toResponse(repository.save(route));
    }

    @Transactional
    public RouteResponse update(String routeCode, UpdateRouteRequest request) {
        InspectionRoute route = getRequired(routeCode);
        route.updateDetails(
                request.routeName().trim(),
                blankToNull(request.description()),
                blankToNull(request.waypointsJson())
        );
        return toResponse(route);
    }

    private InspectionRoute getRequired(String routeCode) {
        String code = routeCode == null ? "" : routeCode.trim();
        return repository.findByRouteCode(code)
                .orElseThrow(() -> new NoSuchElementException(
                        "航线不存在：" + code
                ));
    }

    private RouteResponse toResponse(InspectionRoute route) {
        return new RouteResponse(
                route.getRouteCode(),
                route.getRouteName(),
                route.getDescription(),
                route.getWaypointsJson(),
                route.getCreatedAt(),
                route.getUpdatedAt()
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
