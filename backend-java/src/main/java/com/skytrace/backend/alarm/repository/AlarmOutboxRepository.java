package com.skytrace.backend.alarm.repository;

import com.skytrace.backend.alarm.domain.AlarmOutbox;
import com.skytrace.backend.alarm.domain.AlarmOutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AlarmOutboxRepository extends JpaRepository<AlarmOutbox, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from AlarmOutbox o where o.id = :id")
    Optional<AlarmOutbox> lockById(@Param("id") Long id);

    List<AlarmOutbox> findByStatusAndAvailableAtLessThanEqualOrderByIdAsc(
            AlarmOutboxStatus status,
            LocalDateTime availableAt,
            Pageable pageable
    );
}
