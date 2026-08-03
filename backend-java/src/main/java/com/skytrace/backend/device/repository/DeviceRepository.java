package com.skytrace.backend.device.repository;

import com.skytrace.backend.device.domain.Device;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceRepository
        extends JpaRepository<Device, Long> {

    Optional<Device> findByDeviceCode(String deviceCode);

    boolean existsByDeviceCode(String deviceCode);
}
