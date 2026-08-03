package com.uav.backend.route.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "inspection_route")
public class InspectionRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "route_code", nullable = false, unique = true, length = 64)
    private String routeCode;

    @Column(name = "route_name", nullable = false, length = 128)
    private String routeName;

    @Column(length = 512)
    private String description;

    @Column(name = "waypoints_json", columnDefinition = "TEXT")
    private String waypointsJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected InspectionRoute() {
    }

    public InspectionRoute(
            String routeCode,
            String routeName,
            String description,
            String waypointsJson) {
        this.routeCode = routeCode;
        this.routeName = routeName;
        this.description = description;
        this.waypointsJson = waypointsJson;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void updateDetails(
            String routeName,
            String description,
            String waypointsJson) {
        this.routeName = routeName;
        this.description = description;
        this.waypointsJson = waypointsJson;
        this.updatedAt = LocalDateTime.now();
    }

    public String getRouteCode() {
        return routeCode;
    }

    public String getRouteName() {
        return routeName;
    }

    public String getDescription() {
        return description;
    }

    public String getWaypointsJson() {
        return waypointsJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
