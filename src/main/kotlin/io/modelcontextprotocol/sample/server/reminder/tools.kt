package io.modelcontextprotocol.sample.server.reminder

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ReminderTools")
private val json = Json { ignoreUnknownKeys = true }

/**
 * Handles the reminder.create tool call.
 */
suspend fun handleCreateReminder(
    service: ReminderService,
    arguments: Map<String, Any?>?,
): CallToolResult {
    logger.debug("handleCreateReminder called with arguments: $arguments")

    if (arguments == null) {
        return CallToolResult(
            content = listOf(TextContent("Error: Missing arguments")),
            isError = true,
        )
    }

    return try {
        val input = parseCreateReminderInput(arguments)
        val output = service.createReminder(input)
        val resultJson = json.encodeToString(CreateReminderOutput.serializer(), output)

        CallToolResult(
            content = listOf(TextContent(resultJson)),
            isError = false,
        )
    } catch (e: IllegalArgumentException) {
        logger.error("Invalid input for createReminder", e)
        CallToolResult(
            content = listOf(TextContent("Error: ${e.message}")),
            isError = true,
        )
    } catch (e: Exception) {
        logger.error("Unexpected error in createReminder", e)
        CallToolResult(
            content = listOf(TextContent("Error: Unexpected error: ${e.message}")),
            isError = true,
        )
    }
}

/**
 * Handles the reminder.list tool call.
 */
suspend fun handleListReminders(
    service: ReminderService,
    arguments: Map<String, Any?>?,
): CallToolResult {
    logger.debug("handleListReminders called with arguments: $arguments")

    return try {
        val input = parseListRemindersInput(arguments ?: emptyMap())
        val output = service.listReminders(input)
        val resultJson = json.encodeToString(ListRemindersOutput.serializer(), output)

        CallToolResult(
            content = listOf(TextContent(resultJson)),
            isError = false,
        )
    } catch (e: IllegalArgumentException) {
        logger.error("Invalid input for listReminders", e)
        CallToolResult(
            content = listOf(TextContent("Error: ${e.message}")),
            isError = true,
        )
    } catch (e: Exception) {
        logger.error("Unexpected error in listReminders", e)
        CallToolResult(
            content = listOf(TextContent("Error: Unexpected error: ${e.message}")),
            isError = true,
        )
    }
}

/**
 * Handles the reminder.cancel tool call.
 */
suspend fun handleCancelReminder(
    service: ReminderService,
    arguments: Map<String, Any?>?,
): CallToolResult {
    logger.debug("handleCancelReminder called with arguments: $arguments")

    if (arguments == null) {
        return CallToolResult(
            content = listOf(TextContent("Error: Missing arguments")),
            isError = true,
        )
    }

    val id = extractString(arguments, "id")
    if (id.isNullOrBlank()) {
        return CallToolResult(
            content = listOf(TextContent("Error: 'id' argument is required")),
            isError = true,
        )
    }

    return try {
        val cancelled = service.cancelReminder(id)
        val result = if (cancelled) {
            "Reminder $id cancelled successfully"
        } else {
            "Reminder $id not found or already cancelled"
        }

        CallToolResult(
            content = listOf(TextContent(result)),
            isError = false,
        )
    } catch (e: Exception) {
        logger.error("Unexpected error in cancelReminder", e)
        CallToolResult(
            content = listOf(TextContent("Error: Unexpected error: ${e.message}")),
            isError = true,
        )
    }
}

/**
 * Handles the reminder.claimDue tool call.
 */
suspend fun handleClaimDue(
    service: ReminderService,
    arguments: Map<String, Any?>?,
): CallToolResult {
    logger.debug("handleClaimDue called with arguments: $arguments")

    if (arguments == null) {
        return CallToolResult(
            content = listOf(TextContent("Error: Missing arguments")),
            isError = true,
        )
    }

    return try {
        val input = parseClaimDueInput(arguments)
        val output = service.claimDueEvents(input)
        val resultJson = json.encodeToString(ClaimDueOutput.serializer(), output)

        CallToolResult(
            content = listOf(TextContent(resultJson)),
            isError = false,
        )
    } catch (e: IllegalArgumentException) {
        logger.error("Invalid input for claimDue", e)
        CallToolResult(
            content = listOf(TextContent("Error: ${e.message}")),
            isError = true,
        )
    } catch (e: Exception) {
        logger.error("Unexpected error in claimDue", e)
        CallToolResult(
            content = listOf(TextContent("Error: Unexpected error: ${e.message}")),
            isError = true,
        )
    }
}

/**
 * Handles the reminder.ackSent tool call.
 */
suspend fun handleAckSent(
    service: ReminderService,
    arguments: Map<String, Any?>?,
): CallToolResult {
    logger.debug("handleAckSent called with arguments: $arguments")

    if (arguments == null) {
        return CallToolResult(
            content = listOf(TextContent("Error: Missing arguments")),
            isError = true,
        )
    }

    return try {
        val input = parseAckSentInput(arguments)
        val output = service.acknowledgeSent(input)
        val resultJson = json.encodeToString(AckSentOutput.serializer(), output)

        CallToolResult(
            content = listOf(TextContent(resultJson)),
            isError = false,
        )
    } catch (e: IllegalArgumentException) {
        logger.error("Invalid input for ackSent", e)
        CallToolResult(
            content = listOf(TextContent("Error: ${e.message}")),
            isError = true,
        )
    } catch (e: Exception) {
        logger.error("Unexpected error in ackSent", e)
        CallToolResult(
            content = listOf(TextContent("Error: Unexpected error: ${e.message}")),
            isError = true,
        )
    }
}

/**
 * Handles the reminder.fail tool call.
 */
suspend fun handleFail(
    service: ReminderService,
    arguments: Map<String, Any?>?,
): CallToolResult {
    logger.debug("handleFail called with arguments: $arguments")

    if (arguments == null) {
        return CallToolResult(
            content = listOf(TextContent("Error: Missing arguments")),
            isError = true,
        )
    }

    return try {
        val input = parseFailInput(arguments)
        val output = service.markFailed(input)
        val resultJson = json.encodeToString(FailOutput.serializer(), output)

        CallToolResult(
            content = listOf(TextContent(resultJson)),
            isError = false,
        )
    } catch (e: IllegalArgumentException) {
        logger.error("Invalid input for fail", e)
        CallToolResult(
            content = listOf(TextContent("Error: ${e.message}")),
            isError = true,
        )
    } catch (e: Exception) {
        logger.error("Unexpected error in fail", e)
        CallToolResult(
            content = listOf(TextContent("Error: Unexpected error: ${e.message}")),
            isError = true,
        )
    }
}

// Helper functions for parsing arguments

private fun parseCreateReminderInput(args: Map<String, Any?>): CreateReminderInput {
    val title = extractString(args, "title")
        ?: throw IllegalArgumentException("'title' is required")

    val description = extractString(args, "description")
    val triggerAt = extractString(args, "triggerAt")
        ?: throw IllegalArgumentException("'triggerAt' is required")
    val timezone = extractString(args, "timezone")
    val metadata = extractString(args, "metadata")

    return CreateReminderInput(
        title = title,
        description = description,
        triggerAt = triggerAt,
        timezone = timezone,
        metadata = metadata,
    )
}

private fun parseListRemindersInput(args: Map<String, Any?>): ListRemindersInput {
    val status = extractString(args, "status")
    val from = extractString(args, "from")
    val to = extractString(args, "to")
    val limit = extractInt(args, "limit")

    return ListRemindersInput(
        status = status,
        from = from,
        to = to,
        limit = limit,
    )
}

private fun parseClaimDueInput(args: Map<String, Any?>): ClaimDueInput {
    val now = extractString(args, "now")
    val limit = extractInt(args, "limit") ?: 10
    val claimerId = extractString(args, "claimerId")
        ?: throw IllegalArgumentException("'claimerId' is required")

    return ClaimDueInput(
        now = now,
        limit = limit,
        claimerId = claimerId,
    )
}

private fun parseAckSentInput(args: Map<String, Any?>): AckSentInput {
    val claimToken = extractString(args, "claimToken")
        ?: throw IllegalArgumentException("'claimToken' is required")

    val eventIds = extractStringList(args, "eventIds")
        ?: throw IllegalArgumentException("'eventIds' is required")

    val sentAt = extractString(args, "sentAt")

    return AckSentInput(
        claimToken = claimToken,
        eventIds = eventIds,
        sentAt = sentAt,
    )
}

private fun parseFailInput(args: Map<String, Any?>): FailInput {
    val claimToken = extractString(args, "claimToken")
        ?: throw IllegalArgumentException("'claimToken' is required")

    val eventIds = extractStringList(args, "eventIds")
        ?: throw IllegalArgumentException("'eventIds' is required")

    val error = extractString(args, "error")
        ?: throw IllegalArgumentException("'error' is required")

    return FailInput(
        claimToken = claimToken,
        eventIds = eventIds,
        error = error,
    )
}

private fun extractString(args: Map<String, Any?>, key: String): String? {
    return when (val value = args[key]) {
        is String -> value
        is JsonElement -> value.jsonPrimitive.content
        null -> null
        else -> value.toString()
    }
}

private fun extractInt(args: Map<String, Any?>, key: String): Int? {
    return when (val value = args[key]) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        is JsonElement -> value.jsonPrimitive.content.toIntOrNull()
        null -> null
        else -> null
    }
}

private fun extractStringList(args: Map<String, Any?>, key: String): List<String>? {
    return when (val value = args[key]) {
        is List<*> -> value.mapNotNull { item ->
            when (item) {
                is String -> item
                is JsonElement -> item.jsonPrimitive.content
                else -> item?.toString()
            }
        }
        is JsonElement -> {
            // Try to parse as JSON array
            try {
                json.decodeFromString<List<String>>(value.toString())
            } catch (e: Exception) {
                null
            }
        }
        null -> null
        else -> null
    }
}

