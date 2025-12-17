package io.modelcontextprotocol.sample.server.reminder

import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.time.Clock
import java.time.Instant

private val logger = LoggerFactory.getLogger("ReminderDatabase")

/**
 * Manages SQLite database connection and migrations.
 */
class ReminderDatabase(
    private val dbPath: String,
    private val clock: Clock,
) {
    private val dbConnection: Connection by lazy {
        DriverManager.getConnection("jdbc:sqlite:$dbPath").also {
            it.autoCommit = false
            runMigrations(it)
        }
    }

    /**
     * Gets a database connection. The connection is shared and should not be closed.
     */
    fun getConnection(): Connection = dbConnection

    /**
     * Runs database migrations.
     */
    private fun runMigrations(conn: Connection) {
        try {
            // Create schema version table if it doesn't exist
            conn.createStatement().use { stmt ->
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version INTEGER PRIMARY KEY,
                        applied_at TEXT NOT NULL
                    )
                """.trimIndent())
            }

            // Get current version
            val currentVersion = conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT MAX(version) as version FROM schema_version").use { rs ->
                    if (rs.next()) rs.getInt("version") else 0
                }
            }

            logger.info("Current database schema version: $currentVersion")

            // Run migrations
            if (currentVersion < 1) {
                migrateToV1(conn)
                recordMigration(conn, 1)
            }

            conn.commit()
            logger.info("Database migrations completed successfully")
        } catch (e: SQLException) {
            logger.error("Migration failed", e)
            conn.rollback()
            throw RuntimeException("Database migration failed", e)
        }
    }

    private fun migrateToV1(conn: Connection) {
        logger.info("Running migration to version 1")

        // Create reminders table
        conn.createStatement().use { stmt ->
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS reminders (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    description TEXT,
                    trigger_at TEXT NOT NULL,
                    timezone TEXT,
                    created_at TEXT NOT NULL,
                    status TEXT NOT NULL CHECK(status IN ('PENDING', 'CANCELLED', 'COMPLETED')),
                    metadata_json TEXT
                )
            """.trimIndent())
        }

        // Create reminder_events table
        conn.createStatement().use { stmt ->
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS reminder_events (
                    id TEXT PRIMARY KEY,
                    reminder_id TEXT NOT NULL,
                    trigger_at TEXT NOT NULL,
                    claimed_at TEXT,
                    claim_token TEXT,
                    sent_at TEXT,
                    attempts INTEGER NOT NULL DEFAULT 0,
                    last_error TEXT,
                    FOREIGN KEY (reminder_id) REFERENCES reminders(id)
                )
            """.trimIndent())
        }

        // Create indexes for performance
        conn.createStatement().use { stmt ->
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_reminders_status ON reminders(status)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_reminders_trigger_at ON reminders(trigger_at)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_events_reminder_id ON reminder_events(reminder_id)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_events_trigger_at ON reminder_events(trigger_at)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_events_claim_token ON reminder_events(claim_token)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_events_claimed_at ON reminder_events(claimed_at)")
        }
    }

    private fun recordMigration(conn: Connection, version: Int) {
        conn.prepareStatement("INSERT INTO schema_version (version, applied_at) VALUES (?, ?)").use { stmt ->
            stmt.setInt(1, version)
            stmt.setString(2, Instant.now(clock).toString())
            stmt.executeUpdate()
        }
    }

    /**
     * Closes the database connection.
     */
    fun close() {
        try {
            dbConnection.close()
            logger.info("Database connection closed")
        } catch (e: SQLException) {
            logger.error("Error closing database connection", e)
        }
    }
}

