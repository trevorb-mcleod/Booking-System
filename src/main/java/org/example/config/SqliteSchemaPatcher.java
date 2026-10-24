package org.example.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * SQLite + ddl-auto=update does not always add columns to existing tables.
 * Applies lightweight patches for schema drift on startup.
 */
@Component
public class SqliteSchemaPatcher {

    private final JdbcTemplate jdbc;

    public SqliteSchemaPatcher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void patchAppointmentsTable() {
        addColumnIfMissing("appointments", "reminder_channel", "TEXT DEFAULT 'EMAIL'");
        addColumnIfMissing("appointments", "reminder_sent", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing("appointments", "reminder_phone", "TEXT");
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        if (columnExists(table, column)) return;
        jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        System.out.printf("[SqliteSchemaPatcher] Added %s.%s%n", table, column);
    }

    private boolean columnExists(String table, String column) {
        return jdbc.queryForList("PRAGMA table_info(" + table + ")").stream()
                .anyMatch(row -> column.equalsIgnoreCase(String.valueOf(row.get("name"))));
    }
}
