package com.example.log_ingestion.repository;

import com.example.log_ingestion.domain.RawLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

public interface RawLogRepository extends JpaRepository<RawLog, Long> {
    Optional<RawLog> findByEventId(UUID eventId);
}
