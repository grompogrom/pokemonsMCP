package io.modelcontextprotocol.sample.server.reminder

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Status of a reminder.
 */
enum class ReminderStatus {
    PENDING,
    CANCELLED,
    COMPLETED
}

/**
 * Represents a reminder entity.
 */
data class Reminder(
    val id: String,
    val title: String,
    val description: String?,
    val triggerAt: Instant,
    val timezone: String?,
    val createdAt: Instant,
    val status: ReminderStatus,
    val metadataJson: String? = null,
)

/**
 * Represents a reminder event in the queue.
 */
data class ReminderEvent(
    val id: String,
    val reminderId: String,
    val triggerAt: Instant,
    val claimedAt: Instant?,
    val claimToken: String?,
    val sentAt: Instant?,
    val attempts: Int,
    val lastError: String?,
)

/**
 * Represents a claimed event with reminder details for LLM consumption.
 */
data class ClaimedEvent(
    val eventId: String,
    val reminderId: String,
    val title: String,
    val description: String?,
    val triggerAt: Instant,
    val timezone: String?,
    val claimToken: String,
)

/**
 * Input DTOs for MCP tools.
 */
@Serializable
data class CreateReminderInput(
    val title: String,
    val description: String? = null,
    val triggerAt: String, // RFC3339/ISO-8601
    val timezone: String? = null,
    val metadata: String? = null,
)

@Serializable
data class ListRemindersInput(
    val status: String? = null,
    val from: String? = null, // RFC3339/ISO-8601
    val to: String? = null, // RFC3339/ISO-8601
    val limit: Int? = null,
)

@Serializable
data class ClaimDueInput(
    val now: String? = null, // RFC3339/ISO-8601, optional override for testing
    val limit: Int = 10,
    val claimerId: String,
)

@Serializable
data class AckSentInput(
    val claimToken: String,
    val eventIds: List<String>,
    val sentAt: String? = null, // RFC3339/ISO-8601, optional override
)

@Serializable
data class FailInput(
    val claimToken: String,
    val eventIds: List<String>,
    val error: String,
)

/**
 * Output DTOs for MCP tools.
 */
@Serializable
data class CreateReminderOutput(
    val id: String,
    val triggerAt: String, // RFC3339/ISO-8601 normalized to UTC
)

@Serializable
data class ReminderOutput(
    val id: String,
    val title: String,
    val description: String?,
    val triggerAt: String,
    val timezone: String?,
    val createdAt: String,
    val status: String,
    val metadata: String?,
)

@Serializable
data class ListRemindersOutput(
    val reminders: List<ReminderOutput>,
)

@Serializable
data class ClaimedEventOutput(
    val eventId: String,
    val reminderId: String,
    val title: String,
    val description: String?,
    val triggerAt: String,
    val timezone: String?,
    val claimToken: String,
)

@Serializable
data class ClaimDueOutput(
    val claimToken: String,
    val events: List<ClaimedEventOutput>,
)

@Serializable
data class AckSentOutput(
    val acknowledged: Int,
)

@Serializable
data class FailOutput(
    val failed: Int,
)

