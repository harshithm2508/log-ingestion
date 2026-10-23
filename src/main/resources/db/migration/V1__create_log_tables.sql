CREATE TABLE raw_logs(
    id          BIGINT          GENERATED ALWAYS AS IDENTITY,
    event_id    UUID            NOT NULL,
    source_id   VARCHAR(64)     NOT NULL,
    received_at TIMESTAMPTZ     NOT NULL,
    raw_message TEXT            NOT NULL,
    processing_status   VARCHAR(16)     NOT NULL,
    attempt_count   INTEGER NOT NULL DEFAULT 0,
    last_error_code VARCHAR(64),
    last_error_message VARCHAR(500),
    processed_at TIMESTAMPTZ,
    updated_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_raw_logs PRIMARY KEY(id),
    CONSTRAINT uq_raw_logs_event_id UNIQUE(event_id),
    CONSTRAINT ck_raw_logs_processing_status
        CHECK (processing_status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED')),
    CONSTRAINT ck_raw_logs_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_raw_logs_processed_at
        CHECK ((processing_status = 'PROCESSED') = (processed_at IS NOT NULL))
);

CREATE INDEX ix_raw_logs_received_at_event_id ON raw_logs (received_at DESC, event_id DESC);
CREATE INDEX ix_raw_logs_processing_status ON raw_logs (processing_status);

CREATE TABLE normalized_logs (
    id               BIGINT        GENERATED ALWAYS AS IDENTITY,
    event_id         UUID          NOT NULL,
    event_timestamp  TIMESTAMPTZ   NOT NULL,
    severity         VARCHAR(8)    NOT NULL,
    service_name     VARCHAR(128)  NOT NULL,
    message          TEXT          NOT NULL,
    schema_version   SMALLINT      NOT NULL DEFAULT 1,
    created_at       TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_normalized_logs PRIMARY KEY (id),
    CONSTRAINT uq_normalized_logs_event_id UNIQUE (event_id),
    CONSTRAINT fk_normalized_logs_raw_logs FOREIGN KEY (event_id)
        REFERENCES raw_logs (event_id) ON DELETE RESTRICT,
    CONSTRAINT ck_normalized_logs_severity
        CHECK (severity IN ('TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL')),
    CONSTRAINT ck_normalized_logs_service_name CHECK (service_name ~ '\S'),
    CONSTRAINT ck_normalized_logs_message CHECK (message ~ '\S'),
    CONSTRAINT ck_normalized_logs_schema_version CHECK (schema_version >= 1)
);
-- Event list: ?severity= and ?serviceName= filters.
CREATE INDEX ix_normalized_logs_severity ON normalized_logs (severity);
CREATE INDEX ix_normalized_logs_service_name ON normalized_logs (service_name);
