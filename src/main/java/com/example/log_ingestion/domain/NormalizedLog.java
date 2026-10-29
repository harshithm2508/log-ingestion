package com.example.log_ingestion.domain;

import com.example.log_ingestion.domain.enums.Severity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "normalized_logs")
public class NormalizedLog {
    public static final short CURRENT_SCHEMA_VERSION = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private UUID eventId;

    @Column(nullable = false, updatable = false)
    private Instant eventTimestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 8)
    private Severity severity;

    @Column(nullable = false, updatable = false, length = 128)
    private String serviceName;

    @Column(nullable = false, updatable = false)
    private String message;

    @Column(nullable = false, updatable = false)
    private short schemaVersion;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;


    public static NormalizedLog create(UUID eventId, Instant eventTimestamp, Severity severity, String serviceName, String message, Instant createdAt){
        NormalizedLog normalizedLog = new NormalizedLog();
        normalizedLog.eventId = Objects.requireNonNull(eventId, "eventId shouldn't be null");
        normalizedLog.eventTimestamp = Objects.requireNonNull(eventTimestamp, "event timestamp shouldn't be null");
        normalizedLog.severity = Objects.requireNonNull(severity, "severity shouldn't be null");
        normalizedLog.serviceName = Objects.requireNonNull(serviceName, "service name shouldn't be null");
        normalizedLog.message = Objects.requireNonNull(message, "message shouldn't be null");
        normalizedLog.createdAt = Objects.requireNonNull(createdAt, "created at shouldn't be null");
        normalizedLog.schemaVersion = CURRENT_SCHEMA_VERSION;
        return normalizedLog;
    }

    @Override
    public boolean equals(Object o){
        if(this == o){
            return true;
        }

        if(!(o instanceof NormalizedLog other)){
            return false;
        }

        return eventId != null && eventId.equals(other.getEventId());
    }

    @Override
    public int hashCode(){
        return Objects.hashCode(eventId);
    }
}
