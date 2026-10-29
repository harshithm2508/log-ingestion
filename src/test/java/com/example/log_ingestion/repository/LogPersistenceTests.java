package com.example.log_ingestion.repository;

import com.example.log_ingestion.TestContainersConfiguration;
import com.example.log_ingestion.domain.NormalizedLog;
import com.example.log_ingestion.domain.RawLog;
import com.example.log_ingestion.domain.enums.ProcessingStatus;
import com.example.log_ingestion.domain.enums.Severity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Runs against real PostgreSQL (Testcontainers) with the Flyway schema. Each test rolls back.
@DataJpaTest
@Import(TestContainersConfiguration.class)
class LogPersistenceTests {

    // Microsecond precision, because that is what TIMESTAMPTZ stores.
    private static final Instant RECEIVED_AT = Instant.parse("2026-10-29T10:00:00.123456Z");
    private static final Instant STARTED_AT = Instant.parse("2026-10-29T10:00:01Z");
    private static final Instant FINISHED_AT = Instant.parse("2026-10-29T10:00:02Z");

    @Autowired
    private RawLogRepository rawLogRepository;

    @Autowired
    private NormalizedLogRepository normalizedLogRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rawMessageIsStoredExactlyAsReceived() {
        String rawMessage = "  \t2026-10-29T10:00:00Z ERROR payment-service Card declined | code=51 | ünïcødé ✓ 日本語 🚀\r\n"
                + "second line with trailing spaces   \n";
        UUID eventId = UUID.randomUUID();
        rawLogRepository.saveAndFlush(RawLog.pending(eventId, "dev-source", rawMessage, RECEIVED_AT));
        // Without clear() the read below would come from Hibernate's cache, not from Postgres.
        entityManager.clear();

        RawLog reloaded = rawLogRepository.findByEventId(eventId).orElseThrow();

        assertThat(reloaded.getRawMessage()).isEqualTo(rawMessage);
        String storedInDatabase = jdbcTemplate.queryForObject(
                "SELECT raw_message FROM raw_logs WHERE event_id = ?", String.class, eventId);
        assertThat(storedInDatabase).isEqualTo(rawMessage);
    }

    @Test
    void timestampsSurviveTheRoundTripInUtc() {
        UUID eventId = UUID.randomUUID();
        rawLogRepository.saveAndFlush(RawLog.pending(eventId, "dev-source", "message", RECEIVED_AT));
        entityManager.clear();

        RawLog reloaded = rawLogRepository.findByEventId(eventId).orElseThrow();

        assertThat(reloaded.getReceivedAt()).isEqualTo(RECEIVED_AT);
        assertThat(reloaded.getUpdatedAt()).isEqualTo(RECEIVED_AT);
        // Check the stored value itself, independent of any JVM or JDBC time-zone setting.
        String storedAsUtc = jdbcTemplate.queryForObject(
                "SELECT to_char(received_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US') FROM raw_logs WHERE event_id = ?",
                String.class, eventId);
        assertThat(storedAsUtc).isEqualTo("2026-10-29T10:00:00.123456");
    }

    @Test
    void everyProcessingStatusCanBeStored() {
        RawLog pending = rawLog();
        RawLog processing = rawLog();
        processing.startProcessing(STARTED_AT);
        RawLog processed = rawLog();
        processed.startProcessing(STARTED_AT);
        processed.markProcessed(FINISHED_AT);
        RawLog failed = rawLog();
        failed.startProcessing(STARTED_AT);
        failed.markFailed("INVALID_TIMESTAMP", "Timestamp could not be parsed", FINISHED_AT);

        rawLogRepository.saveAllAndFlush(List.of(pending, processing, processed, failed));
        entityManager.clear();

        assertThat(reload(pending).getProcessingStatus()).isEqualTo(ProcessingStatus.PENDING);
        assertThat(reload(processing).getProcessingStatus()).isEqualTo(ProcessingStatus.PROCESSING);
        RawLog reloadedProcessed = reload(processed);
        assertThat(reloadedProcessed.getProcessingStatus()).isEqualTo(ProcessingStatus.PROCESSED);
        assertThat(reloadedProcessed.getProcessedAt()).isEqualTo(FINISHED_AT);
        RawLog reloadedFailed = reload(failed);
        assertThat(reloadedFailed.getProcessingStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(reloadedFailed.getAttemptCount()).isEqualTo(1);
        assertThat(reloadedFailed.getLastErrorCode()).isEqualTo("INVALID_TIMESTAMP");
        assertThat(reloadedFailed.getLastErrorMessage()).isEqualTo("Timestamp could not be parsed");
        assertThat(reloadedFailed.getProcessedAt()).isNull();
    }

    @Test
    void statusChangeIsWrittenToTheDatabase() {
        RawLog rawLog = rawLogRepository.saveAndFlush(rawLog());

        rawLog.startProcessing(STARTED_AT);
        rawLog.markProcessed(FINISHED_AT);
        entityManager.flush();
        entityManager.clear();

        RawLog reloaded = reload(rawLog);
        assertThat(reloaded.getProcessingStatus()).isEqualTo(ProcessingStatus.PROCESSED);
        assertThat(reloaded.getAttemptCount()).isEqualTo(1);
        assertThat(reloaded.getProcessedAt()).isEqualTo(FINISHED_AT);
        assertThat(reloaded.getUpdatedAt()).isEqualTo(FINISHED_AT);
    }

    @Test
    void unknownEventIdIsNotFound() {
        assertThat(rawLogRepository.findByEventId(UUID.randomUUID())).isEmpty();
        assertThat(normalizedLogRepository.findByEventId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void duplicateRawEventIdIsRejected() {
        UUID eventId = UUID.randomUUID();
        rawLogRepository.saveAndFlush(RawLog.pending(eventId, "dev-source", "first", RECEIVED_AT));

        assertThatThrownBy(() -> rawLogRepository.saveAndFlush(
                RawLog.pending(eventId, "dev-source", "second", RECEIVED_AT)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_raw_logs_event_id");
    }

    @Test
    void normalizedLogIsStoredAndLinkedToItsRawEvent() {
        RawLog rawLog = rawLogRepository.saveAndFlush(rawLog());
        normalizedLogRepository.saveAndFlush(normalizedLog(rawLog.getEventId()));
        entityManager.clear();

        NormalizedLog reloaded = normalizedLogRepository.findByEventId(rawLog.getEventId()).orElseThrow();

        assertThat(reloaded.getEventTimestamp()).isEqualTo(Instant.parse("2026-10-29T09:59:59Z"));
        assertThat(reloaded.getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(reloaded.getServiceName()).isEqualTo("payment-service");
        assertThat(reloaded.getMessage()).isEqualTo("Card declined");
        assertThat(reloaded.getSchemaVersion()).isEqualTo(NormalizedLog.CURRENT_SCHEMA_VERSION);
        assertThat(reloaded.getCreatedAt()).isEqualTo(FINISHED_AT);
    }

    @Test
    void normalizedLogWithoutRawEventIsRejected() {
        assertThatThrownBy(() -> normalizedLogRepository.saveAndFlush(normalizedLog(UUID.randomUUID())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_normalized_logs_raw_logs");
    }

    @Test
    void secondNormalizedLogForTheSameEventIsRejected() {
        RawLog rawLog = rawLogRepository.saveAndFlush(rawLog());
        normalizedLogRepository.saveAndFlush(normalizedLog(rawLog.getEventId()));

        assertThatThrownBy(() -> normalizedLogRepository.saveAndFlush(normalizedLog(rawLog.getEventId())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_normalized_logs_event_id");
    }

    private static RawLog rawLog() {
        return RawLog.pending(UUID.randomUUID(), "dev-source", "2026-10-29T09:59:59Z ERROR payment-service Card declined", RECEIVED_AT);
    }

    private static NormalizedLog normalizedLog(UUID eventId) {
        return NormalizedLog.create(eventId, Instant.parse("2026-10-29T09:59:59Z"), Severity.ERROR,
                "payment-service", "Card declined", FINISHED_AT);
    }

    private RawLog reload(RawLog rawLog) {
        return rawLogRepository.findByEventId(rawLog.getEventId()).orElseThrow();
    }
}
