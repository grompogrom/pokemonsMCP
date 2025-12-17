package io.modelcontextprotocol.sample.server.reminder

import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

private val logger = LoggerFactory.getLogger("ReminderService")

/**
 * Configuration for the reminder service.
 */
data class ReminderConfig(
    val claimTimeoutMinutes: Int = 5, // Default 5 minutes
)

/**
 * Service layer for reminder business logic.
 */
class ReminderService(
    private val repository: ReminderRepository,
    private val clock: Clock,
    private val config: ReminderConfig = ReminderConfig(),
) {
    /**
     * Creates a new reminder.
     */
    fun createReminder(input: CreateReminderInput): CreateReminderOutput {
        logger.info("Creating reminder: ${input.title}")

        // Parse triggerAt
        val triggerAt = try {
            Instant.parse(input.triggerAt)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("Invalid triggerAt format: ${input.triggerAt}. Expected RFC3339/ISO-8601 format.", e)
        }

        // Normalize to UTC (Instant is always UTC)
        val now = Instant.now(clock)
        val reminderId = UUID.randomUUID().toString()
        val eventId = UUID.randomUUID().toString()

        val reminder = Reminder(
            id = reminderId,
            title = input.title,
            description = input.description,
            triggerAt = triggerAt,
            timezone = input.timezone,
            createdAt = now,
            status = ReminderStatus.PENDING,
            metadataJson = input.metadata,
        )

        val event = ReminderEvent(
            id = eventId,
            reminderId = reminderId,
            triggerAt = triggerAt,
            claimedAt = null,
            claimToken = null,
            sentAt = null,
            attempts = 0,
            lastError = null,
        )

        repository.createReminder(reminder, event)

        return CreateReminderOutput(
            id = reminderId,
            triggerAt = triggerAt.toString(), // Always UTC
        )
    }

    /**
     * Lists reminders with optional filters.
     */
    fun listReminders(input: ListRemindersInput): ListRemindersOutput {
        logger.debug("Listing reminders with filters: status=${input.status}, from=${input.from}, to=${input.to}, limit=${input.limit}")

        val status = input.status?.let {
            try {
                ReminderStatus.valueOf(it.uppercase())
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid status: $it. Valid values: PENDING, CANCELLED, COMPLETED", e)
            }
        }

        val from = input.from?.let {
            try {
                Instant.parse(it)
            } catch (e: DateTimeParseException) {
                throw IllegalArgumentException("Invalid 'from' format: $it. Expected RFC3339/ISO-8601 format.", e)
            }
        }

        val to = input.to?.let {
            try {
                Instant.parse(it)
            } catch (e: DateTimeParseException) {
                throw IllegalArgumentException("Invalid 'to' format: $it. Expected RFC3339/ISO-8601 format.", e)
            }
        }

        val reminders = repository.listReminders(
            status = status,
            from = from,
            to = to,
            limit = input.limit,
        )

        return ListRemindersOutput(
            reminders = reminders.map { reminder ->
                ReminderOutput(
                    id = reminder.id,
                    title = reminder.title,
                    description = reminder.description,
                    triggerAt = reminder.triggerAt.toString(),
                    timezone = reminder.timezone,
                    createdAt = reminder.createdAt.toString(),
                    status = reminder.status.name,
                    metadata = reminder.metadataJson,
                )
            },
        )
    }

    /**
     * Cancels a reminder.
     */
    fun cancelReminder(reminderId: String): Boolean {
        logger.info("Cancelling reminder: $reminderId")
        return repository.cancelReminder(reminderId)
    }

    /**
     * Claims due events atomically.
     */
    fun claimDueEvents(input: ClaimDueInput): ClaimDueOutput {
        logger.info("Claiming due events: claimerId=${input.claimerId}, limit=${input.limit}")

        val now = input.now?.let {
            try {
                Instant.parse(it)
            } catch (e: DateTimeParseException) {
                throw IllegalArgumentException("Invalid 'now' format: $it. Expected RFC3339/ISO-8601 format.", e)
            }
        } ?: Instant.now(clock)

        val claimed = repository.claimDueEvents(
            now = now,
            limit = input.limit,
            claimerId = input.claimerId,
            claimTimeoutMinutes = config.claimTimeoutMinutes,
        )

        if (claimed.isEmpty()) {
            return ClaimDueOutput(
                claimToken = "",
                events = emptyList(),
            )
        }

        // All events should have the same claim token (from the repository)
        val claimToken = claimed.first().first.claimToken
            ?: throw IllegalStateException("Claimed event missing claim token")

        val events = claimed.map { (event, reminder) ->
            ClaimedEventOutput(
                eventId = event.id,
                reminderId = reminder.id,
                title = reminder.title,
                description = reminder.description,
                triggerAt = reminder.triggerAt.toString(),
                timezone = reminder.timezone,
                claimToken = claimToken,
            )
        }

        logger.info("Claimed ${events.size} events with token $claimToken")
        return ClaimDueOutput(
            claimToken = claimToken,
            events = events,
        )
    }

    /**
     * Acknowledges that events were sent.
     */
    fun acknowledgeSent(input: AckSentInput): AckSentOutput {
        logger.info("Acknowledging sent events: claimToken=${input.claimToken}, eventIds=${input.eventIds.size}")

        val sentAt = input.sentAt?.let {
            try {
                Instant.parse(it)
            } catch (e: DateTimeParseException) {
                throw IllegalArgumentException("Invalid 'sentAt' format: $it. Expected RFC3339/ISO-8601 format.", e)
            }
        } ?: Instant.now(clock)

        val acknowledged = repository.acknowledgeSent(
            claimToken = input.claimToken,
            eventIds = input.eventIds,
            sentAt = sentAt,
        )

        logger.info("Acknowledged $acknowledged events")
        return AckSentOutput(acknowledged = acknowledged)
    }

    /**
     * Marks events as failed.
     */
    fun markFailed(input: FailInput): FailOutput {
        logger.warn("Marking events as failed: claimToken=${input.claimToken}, eventIds=${input.eventIds.size}, error=${input.error}")

        val failed = repository.markFailed(
            claimToken = input.claimToken,
            eventIds = input.eventIds,
            error = input.error,
            claimTimeoutMinutes = config.claimTimeoutMinutes,
        )

        logger.info("Marked $failed events as failed")
        return FailOutput(failed = failed)
    }
}

