package com.skytrace.backend.telemetry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "device_telemetry_point")
public class DeviceTelemetryPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_code", nullable = false, length = 64)
    private String deviceCode;

    @Column(name = "task_code", nullable = false, length = 64)
    private String taskCode;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(precision = 8, scale = 2)
    private BigDecimal altitude;

    @Column(precision = 6, scale = 2)
    private BigDecimal heading;

    @Column(length = 32)
    private String source;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected DeviceTelemetryPoint() {
    }

    public DeviceTelemetryPoint(
            String deviceCode,
            String taskCode,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal altitude,
            BigDecimal heading,
            String source,
            LocalDateTime recordedAt) {
        this.deviceCode = deviceCode;
        this.taskCode = taskCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.heading = heading;
        this.source = source;
        this.recordedAt = recordedAt;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getDeviceCode() {
        return deviceCode;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public BigDecimal getAltitude() {
        return altitude;
    }

    public BigDecimal getHeading() {
        return heading;
    }

    public String getSource() {
        return source;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
