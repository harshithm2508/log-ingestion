package com.example.log_ingestion.repository;

import com.example.log_ingestion.domain.NormalizedLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NormalizedLogRepository extends JpaRepository<NormalizedLog, Long> {
    Optional<NormalizedLog> findByEventId(UUID eventId);
}
