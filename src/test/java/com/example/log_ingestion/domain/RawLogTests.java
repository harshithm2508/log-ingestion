package com.example.log_ingestion.domain;

import com.example.log_ingestion.domain.enums.ProcessingStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Plain JUnit: no Spring context and no database, so these run without Docker.
class RawLogTests {

    private static final UUID EVENT_ID = UUID.fromString("7f6c1a52-3a8e-4b8e-9d0c-2b1f4e6a9c01");
    private static final String SOURCE_ID = "dev-source";
    private static final String RAW_MESSAGE = "2026-10-29T10:00:00Z ERROR payment-service Card declined";
    private static final Instant RECEIVED_AT = Instant.parse("2026-10-29T10:00:00Z");
    private static final Instant STARTED_AT = Instant.parse("2026-10-29T10:00:01Z");
    private static final Instant FINISHED_AT = Instant.parse("2026-10-29T10:00:02Z");
    private static final Instant RETRIED_AT = Instant.parse("2026-10-29T10:05:00Z");
    private static final Instant RETRY_FINISHED_AT = Instant.parse("2026-10-29T10:05:01Z");

    @Test
    void pendingStartsWithNoAttemptsAndNoErrors() {
        RawLog rawLog = pendingLog();

        assertThat(rawLog.getEventId()).isEqualTo(EVENT_ID);
        assertThat(rawLog.getSourceId()).isEqualTo(SOURCE_ID);
        assertThat(rawLog.getRawMessage()).isEqualTo(RAW_MESSAGE);
        assertThat(rawLog.getReceivedAt()).isEqualTo(RECEIVED_AT);
        assertThat(rawLog.getProcessingStatus()).isEqualTo(ProcessingStatus.PENDING);
        assertThat(rawLog.getAttemptCount()).isZero();
        assertThat(rawLog.getUpdatedAt()).isEqualTo(RECEIVED_AT);
        assertThat(rawLog.getProcessedAt()).isNull();
        assertThat(rawLog.getLastErrorCode()).isNull();
        assertThat(rawLog.getLastErrorMessage()).isNull();
    }

    @Test
    void pendingRejectsNullArguments() {
        assertThatNullPointerException()
                .isThrownBy(() -> RawLog.pending(null, SOURCE_ID, RAW_MESSAGE, RECEIVED_AT))
                .withMessageContaining("eventId");
        assertThatNullPointerException()
                .isThrownBy(() -> RawLog.pending(EVENT_ID, null, RAW_MESSAGE, RECEIVED_AT))
                .withMessageContaining("sourceId");
        assertThatNullPointerException()
                .isThrownBy(() -> RawLog.pending(EVENT_ID, SOURCE_ID, null, RECEIVED_AT))
                .withMessageContaining("rawMessage");
        assertThatNullPointerException()
                .isThrownBy(() -> RawLog.pending(EVENT_ID, SOURCE_ID, RAW_MESSAGE, null))
                .withMessageContaining("receivedAt");
    }

    @Test
    void startProcessingCountsTheAttempt() {
        RawLog rawLog = pendingLog();

        rawLog.startProcessing(STARTED_AT);

        assertThat(rawLog.getProcessingStatus()).isEqualTo(ProcessingStatus.PROCESSING);
        assertThat(rawLog.getAttemptCount()).isEqualTo(1);
        assertThat(rawLog.getUpdatedAt()).isEqualTo(STARTED_AT);
        assertThat(rawLog.getProcessedAt()).isNull();
    }

    @Test
    void successfulProcessingSetsProcessedAt() {
        RawLog rawLog = processedLog();

        assertThat(rawLog.getProcessingStatus()).isEqualTo(ProcessingStatus.PROCESSED);
        assertThat(rawLog.getAttemptCount()).isEqualTo(1);
        assertThat(rawLog.getProcessedAt()).isEqualTo(FINISHED_AT);
        assertThat(rawLog.getUpdatedAt()).isEqualTo(FINISHED_AT);
        assertThat(rawLog.getLastErrorCode()).isNull();
        assertThat(rawLog.getLastErrorMessage()).isNull();
    }

    @Test
    void failedProcessingRecordsTheErrorAndLeavesProcessedAtEmpty() {
        RawLog rawLog = failedLog();

        assertThat(rawLog.getProcessingStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(rawLog.getAttemptCount()).isEqualTo(1);
        assertThat(rawLog.getLastErrorCode()).isEqualTo("INVALID_TIMESTAMP");
        assertThat(rawLog.getLastErrorMessage()).isEqualTo("Timestamp could not be parsed");
        assertThat(rawLog.getProcessedAt()).isNull();
        assertThat(rawLog.getUpdatedAt()).isEqualTo(FINISHED_AT);
        assertThat(rawLog.getRawMessage()).isEqualTo(RAW_MESSAGE);
    }

    @Test
    void failedEventCanBeRetried() {
        RawLog rawLog = failedLog();

        rawLog.startProcessing(RETRIED_AT);

        assertThat(rawLog.getProcessingStatus()).isEqualTo(ProcessingStatus.PROCESSING);
        assertThat(rawLog.getAttemptCount()).isEqualTo(2);
        // The previous error stays visible until the retry finishes.
        assertThat(rawLog.getLastErrorCode()).isEqualTo("INVALID_TIMESTAMP");
    }

    @Test
    void successfulRetryClearsThePreviousError() {
        RawLog rawLog = failedLog();
        rawLog.startProcessing(RETRIED_AT);

        rawLog.markProcessed(RETRY_FINISHED_AT);

        assertThat(rawLog.getProcessingStatus()).isEqualTo(ProcessingStatus.PROCESSED);
        assertThat(rawLog.getAttemptCount()).isEqualTo(2);
        assertThat(rawLog.getProcessedAt()).isEqualTo(RETRY_FINISHED_AT);
        assertThat(rawLog.getLastErrorCode()).isNull();
        assertThat(rawLog.getLastErrorMessage()).isNull();
    }

    @Test
    void cannotStartProcessingWhileAlreadyProcessing() {
        RawLog rawLog = processingLog();

        assertThatThrownBy(() -> rawLog.startProcessing(RETRIED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PROCESSING");
        assertUnchanged(rawLog, ProcessingStatus.PROCESSING, 1, STARTED_AT);
    }

    @Test
    void cannotStartProcessingAnAlreadyProcessedEvent() {
        RawLog rawLog = processedLog();

        assertThatThrownBy(() -> rawLog.startProcessing(RETRIED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PROCESSED");
        assertUnchanged(rawLog, ProcessingStatus.PROCESSED, 1, FINISHED_AT);
    }

    @Test
    void cannotMarkProcessedWithoutStartingProcessing() {
        RawLog pending = pendingLog();
        RawLog failed = failedLog();

        assertThatThrownBy(() -> pending.markProcessed(FINISHED_AT))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> failed.markProcessed(RETRY_FINISHED_AT))
                .isInstanceOf(IllegalStateException.class);

        assertUnchanged(pending, ProcessingStatus.PENDING, 0, RECEIVED_AT);
        assertUnchanged(failed, ProcessingStatus.FAILED, 1, FINISHED_AT);
        assertThat(pending.getProcessedAt()).isNull();
        assertThat(failed.getProcessedAt()).isNull();
    }

    @Test
    void cannotMarkFailedWithoutStartingProcessing() {
        RawLog pending = pendingLog();
        RawLog processed = processedLog();

        assertThatThrownBy(() -> pending.markFailed("E", "message", FINISHED_AT))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> processed.markFailed("E", "message", RETRY_FINISHED_AT))
                .isInstanceOf(IllegalStateException.class);

        assertUnchanged(pending, ProcessingStatus.PENDING, 0, RECEIVED_AT);
        assertUnchanged(processed, ProcessingStatus.PROCESSED, 1, FINISHED_AT);
        assertThat(pending.getLastErrorCode()).isNull();
        assertThat(processed.getLastErrorCode()).isNull();
    }

    @Test
    void logsWithTheSameEventIdAreEqual() {
        RawLog first = RawLog.pending(EVENT_ID, SOURCE_ID, "first message", RECEIVED_AT);
        RawLog second = RawLog.pending(EVENT_ID, "other-source", "second message", RETRIED_AT);

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test
    void logsWithDifferentEventIdsAreNotEqual() {
        RawLog first = pendingLog();
        RawLog second = RawLog.pending(UUID.randomUUID(), SOURCE_ID, RAW_MESSAGE, RECEIVED_AT);

        assertThat(first).isNotEqualTo(second);
    }

    private static RawLog pendingLog() {
        return RawLog.pending(EVENT_ID, SOURCE_ID, RAW_MESSAGE, RECEIVED_AT);
    }

    private static RawLog processingLog() {
        RawLog rawLog = pendingLog();
        rawLog.startProcessing(STARTED_AT);
        return rawLog;
    }

    private static RawLog processedLog() {
        RawLog rawLog = processingLog();
        rawLog.markProcessed(FINISHED_AT);
        return rawLog;
    }

    private static RawLog failedLog() {
        RawLog rawLog = processingLog();
        rawLog.markFailed("INVALID_TIMESTAMP", "Timestamp could not be parsed", FINISHED_AT);
        return rawLog;
    }

    // A rejected transition must leave the entity exactly as it was.
    private static void assertUnchanged(RawLog rawLog, ProcessingStatus status, int attemptCount, Instant updatedAt) {
        assertThat(rawLog.getProcessingStatus()).isEqualTo(status);
        assertThat(rawLog.getAttemptCount()).isEqualTo(attemptCount);
        assertThat(rawLog.getUpdatedAt()).isEqualTo(updatedAt);
    }
}
