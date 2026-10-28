package com.example.log_ingestion.domain;

import com.example.log_ingestion.domain.enums.ProcessingStatus;
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
@Table(name = "raw_logs")
public class RawLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private UUID eventId;

    @Column(nullable = false, updatable = false, length = 64)
    private String sourceId;

    @Column(nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(nullable = false, updatable = false)
    private String rawMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProcessingStatus processingStatus;

    @Column(nullable = false)
    private int attemptCount;

    @Column(length = 64)
    private String lastErrorCode;

    @Column(length = 500)
    private String lastErrorMessage;
    private Instant processedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static RawLog pending(UUID eventId, String sourceId, String rawMessage, Instant receivedAt){
        RawLog rawLog = new RawLog();
        rawLog.eventId = Objects.requireNonNull(eventId, "eventId must not be null.");
        rawLog.sourceId = Objects.requireNonNull(sourceId, "sourceId must not be null");
        rawLog.rawMessage = Objects.requireNonNull(rawMessage, "rawMessage must not be null");
        rawLog.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        rawLog.processingStatus = ProcessingStatus.PENDING;
        rawLog.attemptCount = 0;
        rawLog.updatedAt = receivedAt;
        return rawLog;
    }

    public void startProcessing(Instant now) {
        if(this.processingStatus != ProcessingStatus.PENDING && this.processingStatus != ProcessingStatus.FAILED){
            throw new IllegalStateException("Cannot start processing event "+eventId+" in status "+processingStatus);
        }

        this.processingStatus = ProcessingStatus.PROCESSING;
        this.attemptCount++;
        this.updatedAt = now;
    }

    public void markProcessed(Instant now){
        requireStatus(ProcessingStatus.PROCESSING, "mark as processed");
        this.processingStatus = ProcessingStatus.PROCESSED;
        this.processedAt = now;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
        this.updatedAt = now;
    }

    public void markFailed(String errorCode, String errorMessage, Instant now){
        requireStatus(ProcessingStatus.PROCESSING, "mark as failed");
        this.processingStatus = ProcessingStatus.FAILED;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
        this.updatedAt = now;
    }

    private void requireStatus(ProcessingStatus expected, String action){
        if(this.processingStatus != expected){
            throw new IllegalStateException("Cannot "+action+" event "+eventId+" in status "+processingStatus);
        }
    }

    @Override
    public boolean equals(Object o){
        if(this == o){
            return true;
        }

        if(!(o instanceof RawLog other)){
            return false;
        }

        return eventId != null && eventId.equals(other.getEventId());
    }

    @Override
    public int hashCode(){
        return Objects.hashCode(eventId);
    }
}
