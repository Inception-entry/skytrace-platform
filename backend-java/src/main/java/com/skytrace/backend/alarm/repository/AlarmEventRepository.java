package com.skytrace.backend.alarm.repository;

import com.skytrace.backend.alarm.domain.AlarmEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlarmEventRepository extends JpaRepository<AlarmEvent, Long> {
    List<AlarmEvent> findTop20ByOrderByEventTimeDesc();

    boolean existsByDeviceCode(String deviceCode);

    boolean existsByEventCode(String eventCode);
}
