package io.modelcontextprotocol.sample.server.reminder

import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

private val logger = LoggerFactory.getLogger("ReminderRepository")

/**
 * Repository for reminder data access.
 */
class ReminderRepository(
    private val connection: Connection,
) {
    /**
     * Creates a new reminder and its associated event.
     */
    fun createReminder(
        reminder: Reminder,
        event: ReminderEvent,
    ) {
        try {
            // Insert reminder
            connection.prepareStatement("""
                INSERT INTO reminders (id, title, description, trigger_at, timezone, created_at, status, metadata_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()).use { stmt ->
                stmt.setString(1, reminder.id)
                stmt.setString(2, reminder.title)
                stmt.setString(3, reminder.description)
                stmt.setString(4, reminder.triggerAt.toString())
                stmt.setString(5, reminder.timezone)
                stmt.setString(6, reminder.createdAt.toString())
                stmt.setString(7, reminder.status.name)
                stmt.setString(8, reminder.metadataJson)
                stmt.executeUpdate()
            }

            // Insert event
            connection.prepareStatement("""
                INSERT INTO reminder_events (id, reminder_id, trigger_at, claimed_at, claim_token, sent_at, attempts, last_error)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()).use { stmt ->
                stmt.setString(1, event.id)
                stmt.setString(2, event.reminderId)
                stmt.setString(3, event.triggerAt.toString())
                stmt.setString(4, event.claimedAt?.toString())
                stmt.setString(5, event.claimToken)
                stmt.setString(6, event.sentAt?.toString())
                stmt.setInt(7, event.attempts)
                stmt.setString(8, event.lastError)
                stmt.executeUpdate()
            }

            connection.commit()
            logger.debug("Created reminder ${reminder.id} with event ${event.id}")
        } catch (e: SQLException) {
            logger.error("Failed to create reminder", e)
            connection.rollback()
            throw RuntimeException("Failed to create reminder", e)
        }
    }

    /**
     * Lists reminders with optional filters.
     */
    fun listReminders(
        status: ReminderStatus? = null,
        from: Instant? = null,
        to: Instant? = null,
        limit: Int? = null,
    ): List<Reminder> {
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()

        if (status != null) {
            conditions.add("status = ?")
            params.add(status.name)
        }
        if (from != null) {
            conditions.add("trigger_at >= ?")
            params.add(from.toString())
        }
        if (to != null) {
            conditions.add("trigger_at <= ?")
            params.add(to.toString())
        }

        val whereClause = if (conditions.isNotEmpty()) {
            "WHERE ${conditions.joinToString(" AND ")}"
        } else {
            ""
        }

        val limitClause = if (limit != null) {
            "LIMIT ?"
        } else {
            ""
        }

        val sql = """
            SELECT id, title, description, trigger_at, timezone, created_at, status, metadata_json
            FROM reminders
            $whereClause
            ORDER BY trigger_at ASC
            $limitClause
        """.trimIndent()

        return connection.prepareStatement(sql).use { stmt ->
            params.forEachIndexed { index, param ->
                stmt.setString(index + 1, param.toString())
            }
            if (limit != null) {
                stmt.setInt(params.size + 1, limit)
            }

            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            Reminder(
                                id = rs.getString("id"),
                                title = rs.getString("title"),
                                description = rs.getString("description"),
                                triggerAt = Instant.parse(rs.getString("trigger_at")),
                                timezone = rs.getString("timezone"),
                                createdAt = Instant.parse(rs.getString("created_at")),
                                status = ReminderStatus.valueOf(rs.getString("status")),
                                metadataJson = rs.getString("metadata_json"),
                            ),
                        )
                    }
                }
            }
        }
    }

    /**
     * Cancels a reminder and marks its event as cancelled.
     */
    fun cancelReminder(reminderId: String): Boolean {
        return try {
            // Update reminder status
            val updated = connection.prepareStatement("""
                UPDATE reminders SET status = 'CANCELLED' WHERE id = ?
            """.trimIndent()).use { stmt ->
                stmt.setString(1, reminderId)
                stmt.executeUpdate()
            }

            // Delete or mark associated events as cancelled (we'll delete them for simplicity)
            connection.prepareStatement("""
                DELETE FROM reminder_events WHERE reminder_id = ? AND sent_at IS NULL
            """.trimIndent()).use { stmt ->
                stmt.setString(1, reminderId)
                stmt.executeUpdate()
            }

            connection.commit()
            logger.debug("Cancelled reminder $reminderId")
            updated > 0
        } catch (e: SQLException) {
            logger.error("Failed to cancel reminder", e)
            connection.rollback()
            throw RuntimeException("Failed to cancel reminder", e)
        }
    }

    /**
     * Claims due events atomically. Returns the number of claimed events.
     */
    fun claimDueEvents(
        now: Instant,
        limit: Int,
        claimerId: String,
        claimTimeoutMinutes: Int,
    ): List<Pair<ReminderEvent, Reminder>> {
        val claimToken = UUID.randomUUID().toString()
        val claimTimeout = now.minusSeconds(claimTimeoutMinutes * 60L)

        return try {
            // Find eligible events: not sent, trigger_at <= now, and either not claimed or claim expired
            val eligibleEvents = connection.prepareStatement("""
                    SELECT e.id, e.reminder_id, e.trigger_at, e.claimed_at, e.claim_token, e.sent_at, e.attempts, e.last_error
                    FROM reminder_events e
                    INNER JOIN reminders r ON e.reminder_id = r.id
                    WHERE e.sent_at IS NULL
                      AND e.trigger_at <= ?
                      AND r.status = 'PENDING'
                      AND (e.claimed_at IS NULL OR e.claimed_at < ?)
                    ORDER BY e.trigger_at ASC
                    LIMIT ?
                """.trimIndent()).use { stmt ->
                    stmt.setString(1, now.toString())
                    stmt.setString(2, claimTimeout.toString())
                    stmt.setInt(3, limit)

                    stmt.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                add(
                                    ReminderEvent(
                                        id = rs.getString("id"),
                                        reminderId = rs.getString("reminder_id"),
                                        triggerAt = Instant.parse(rs.getString("trigger_at")),
                                        claimedAt = rs.getString("claimed_at")?.let { Instant.parse(it) },
                                        claimToken = rs.getString("claim_token"),
                                        sentAt = rs.getString("sent_at")?.let { Instant.parse(it) },
                                        attempts = rs.getInt("attempts"),
                                        lastError = rs.getString("last_error"),
                                    ),
                                )
                            }
                        }
                    }
                }

                if (eligibleEvents.isEmpty()) {
                    return emptyList()
                }

            // Atomically claim them
            val eventIds = eligibleEvents.map { it.id }
            val placeholders = eventIds.joinToString(",") { "?" }
            val claimedCount = connection.prepareStatement("""
                    UPDATE reminder_events
                    SET claimed_at = ?, claim_token = ?
                    WHERE id IN ($placeholders)
                      AND sent_at IS NULL
                      AND (claimed_at IS NULL OR claimed_at < ?)
                """.trimIndent()).use { stmt ->
                    stmt.setString(1, now.toString())
                    stmt.setString(2, claimToken)
                    eventIds.forEachIndexed { index, id ->
                        stmt.setString(index + 3, id)
                    }
                    stmt.setString(eventIds.size + 3, claimTimeout.toString())
                    stmt.executeUpdate()
                }

            if (claimedCount == 0) {
                // Race condition: events were claimed by another process
                logger.debug("No events were claimed (race condition)")
                return emptyList()
            }

            // Fetch the reminders for claimed events
            val reminderIds = eligibleEvents.map { it.reminderId }.distinct()
            val reminderPlaceholders = reminderIds.joinToString(",") { "?" }
            val reminders = connection.prepareStatement("""
                    SELECT id, title, description, trigger_at, timezone, created_at, status, metadata_json
                    FROM reminders
                    WHERE id IN ($reminderPlaceholders)
                """.trimIndent()).use { stmt ->
                    reminderIds.forEachIndexed { index, id ->
                        stmt.setString(index + 1, id)
                    }

                    stmt.executeQuery().use { rs ->
                        buildMap {
                            while (rs.next()) {
                                val id = rs.getString("id")
                                put(
                                    id,
                                    Reminder(
                                        id = id,
                                        title = rs.getString("title"),
                                        description = rs.getString("description"),
                                        triggerAt = Instant.parse(rs.getString("trigger_at")),
                                        timezone = rs.getString("timezone"),
                                        createdAt = Instant.parse(rs.getString("created_at")),
                                        status = ReminderStatus.valueOf(rs.getString("status")),
                                        metadataJson = rs.getString("metadata_json"),
                                    ),
                                )
                            }
                        }
                    }
                }

            // Fetch the updated events with claim info
            val updatedEvents = connection.prepareStatement("""
                    SELECT id, reminder_id, trigger_at, claimed_at, claim_token, sent_at, attempts, last_error
                    FROM reminder_events
                    WHERE id IN ($placeholders) AND claim_token = ?
                """.trimIndent()).use { stmt ->
                    eventIds.forEachIndexed { index, id ->
                        stmt.setString(index + 1, id)
                    }
                    stmt.setString(eventIds.size + 1, claimToken)

                    stmt.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                add(
                                    ReminderEvent(
                                        id = rs.getString("id"),
                                        reminderId = rs.getString("reminder_id"),
                                        triggerAt = Instant.parse(rs.getString("trigger_at")),
                                        claimedAt = Instant.parse(rs.getString("claimed_at")),
                                        claimToken = rs.getString("claim_token"),
                                        sentAt = rs.getString("sent_at")?.let { Instant.parse(it) },
                                        attempts = rs.getInt("attempts"),
                                        lastError = rs.getString("last_error"),
                                    ),
                                )
                            }
                        }
                    }
                }

            connection.commit()

            // Match events with reminders
            val result = updatedEvents.mapNotNull { event ->
                reminders[event.reminderId]?.let { reminder ->
                    Pair(event, reminder)
                }
            }

            logger.debug("Claimed ${result.size} events with token $claimToken")
            result
        } catch (e: SQLException) {
            logger.error("Failed to claim due events", e)
            connection.rollback()
            throw RuntimeException("Failed to claim due events", e)
        }
    }

    /**
     * Acknowledges that events were sent.
     */
    fun acknowledgeSent(
        claimToken: String,
        eventIds: List<String>,
        sentAt: Instant,
    ): Int {
        return try {
            val placeholders = eventIds.joinToString(",") { "?" }
            val updated = connection.prepareStatement("""
                UPDATE reminder_events
                SET sent_at = ?
                WHERE id IN ($placeholders) AND claim_token = ? AND sent_at IS NULL
            """.trimIndent()).use { stmt ->
                stmt.setString(1, sentAt.toString())
                eventIds.forEachIndexed { index, id ->
                    stmt.setString(index + 2, id)
                }
                stmt.setString(eventIds.size + 2, claimToken)
                stmt.executeUpdate()
            }

            connection.commit()
            logger.debug("Acknowledged $updated events with token $claimToken")
            updated
        } catch (e: SQLException) {
            logger.error("Failed to acknowledge sent events", e)
            connection.rollback()
            throw RuntimeException("Failed to acknowledge sent events", e)
        }
    }

    /**
     * Marks events as failed and releases claim (or keeps it based on attempts).
     */
    fun markFailed(
        claimToken: String,
        eventIds: List<String>,
        error: String,
        claimTimeoutMinutes: Int,
    ): Int {
        return try {
            val placeholders = eventIds.joinToString(",") { "?" }
            val updated = connection.prepareStatement("""
                UPDATE reminder_events
                SET attempts = attempts + 1,
                    last_error = ?,
                    claimed_at = CASE
                        WHEN attempts + 1 >= 3 THEN NULL
                        ELSE claimed_at
                    END,
                    claim_token = CASE
                        WHEN attempts + 1 >= 3 THEN NULL
                        ELSE claim_token
                    END
                WHERE id IN ($placeholders) AND claim_token = ?
            """.trimIndent()).use { stmt ->
                stmt.setString(1, error)
                eventIds.forEachIndexed { index, id ->
                    stmt.setString(index + 2, id)
                }
                stmt.setString(eventIds.size + 2, claimToken)
                stmt.executeUpdate()
            }

            connection.commit()
            logger.debug("Marked $updated events as failed with token $claimToken")
            updated
        } catch (e: SQLException) {
            logger.error("Failed to mark events as failed", e)
            connection.rollback()
            throw RuntimeException("Failed to mark events as failed", e)
        }
    }
}

