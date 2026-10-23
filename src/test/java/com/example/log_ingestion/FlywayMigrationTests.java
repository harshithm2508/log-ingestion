package com.example.log_ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.utility.TestcontainersConfiguration;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FlywayMigrationTests {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesAllMigrationsToAnEmptyDatabase() {
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied).isNotEmpty();
        assertThat(applied).allSatisfy(migration ->
                assertThat(migration.getState()).isEqualTo(MigrationState.SUCCESS));
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void createsLogTables() {
        List<String> tables = jdbcTemplate.queryForList("""
				SELECT table_name
				FROM information_schema.tables
				WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
				ORDER BY table_name
				""", String.class);

        assertThat(tables).containsExactly("flyway_schema_history", "normalized_logs", "raw_logs");
    }

    @Test
    void createsConstraintsTheApplicationReliesOn() {
        List<String> constraints = jdbcTemplate.queryForList("""
				SELECT constraint_name
				FROM information_schema.table_constraints
				WHERE table_schema = 'public' AND table_name IN ('raw_logs', 'normalized_logs')
				""", String.class);

        assertThat(constraints).contains(
                "uq_raw_logs_event_id",
                "uq_normalized_logs_event_id",
                "fk_normalized_logs_raw_logs",
                "ck_raw_logs_processing_status",
                "ck_normalized_logs_severity");
    }

}
