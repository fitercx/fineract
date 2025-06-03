package org.apache.fineract.infrastructure.core.service.database;

/**
 * Enum representing supported database types in the application.
 * This is a mock implementation for testing purposes.
 */
public enum DatabaseType {
    MYSQL,
    POSTGRESQL;

    /**
     * Checks if the database type is PostgreSQL.
     *
     * @return true if the database type is PostgreSQL, false otherwise
     */
    public boolean isPostgres() {
        return this == POSTGRESQL;
    }
}
