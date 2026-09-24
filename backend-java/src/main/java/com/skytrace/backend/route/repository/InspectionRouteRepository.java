package com.skytrace.backend.route.repository;

import com.skytrace.backend.route.domain.InspectionRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InspectionRouteRepository
        extends JpaRepository<InspectionRoute, Long> {

    Optional<InspectionRoute> findByRouteCode(String routeCode);

    List<InspectionRoute> findByRouteCodeIn(Collection<String> routeCodes);

    boolean existsByRouteCode(String routeCode);
}
