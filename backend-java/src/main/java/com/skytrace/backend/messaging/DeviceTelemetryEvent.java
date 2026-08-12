package com.skytrace.backend.messaging;

/**
 * 设备实时遥测事件，经 fanout 推到 Node → Socket.IO {@code device.telemetry}。
 */
public record DeviceTelemetryEvent(
        String type,
        String deviceCode,
        String ts,
        String source,
        double latitude,
        double longitude,
        Double altitude,
        Double heading
) {
    public static final String TYPE = "device.telemetry";

    public static DeviceTelemetryEvent of(
            String deviceCode,
            String ts,
            String source,
            double latitude,
            double longitude,
            Double altitude,
            Double heading) {
        return new DeviceTelemetryEvent(
                TYPE,
                deviceCode,
                ts,
                source,
                latitude,
                longitude,
                altitude,
                heading
        );
    }
}
