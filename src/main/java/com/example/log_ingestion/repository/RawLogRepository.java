package com.example.log_ingestion.repository;

import com.example.log_ingestion.domain.RawLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RawLogRepository extends JpaRepository<RawLog, Long> {
    Optional<RawLog> findByEventId(UUID eventId);
}
