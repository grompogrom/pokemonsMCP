package io.modelcontextprotocol.sample.server.reminder

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Clock
import java.time.ZoneId

private val logger = LoggerFactory.getLogger("ReminderModule")

/**
 * Configuration for the reminder module.
 */
data class ReminderModuleConfig(
    val dbPath: String = "./reminders.db",
    val claimTimeoutMinutes: Int = 5,
)

/**
 * Main entry point for the reminder module.
 * Installs reminder tools into the MCP server.
 */
object ReminderModule {
    /**
     * Installs the reminder module into the MCP server.
     *
     * @param server The MCP server instance
     * @param clock Clock for time operations (injectable for testing)
     * @param config Configuration for the reminder module
     */
    fun install(
        server: Server,
        clock: Clock = Clock.systemUTC(),
        config: ReminderModuleConfig = ReminderModuleConfig(),
    ) {
        logger.info("Installing reminder module with database: ${config.dbPath}")

        // Initialize database
        val db = ReminderDatabase(config.dbPath, clock)
        val repository = ReminderRepository(db.getConnection())
        val service = ReminderService(
            repository = repository,
            clock = clock,
            config = ReminderConfig(claimTimeoutMinutes = config.claimTimeoutMinutes),
        )

        // Register reminder.create tool
        server.addTool(
            name = "reminder.create",
            description = "Create a new reminder/event. Stores the reminder and creates a queued event for delivery.",
            inputSchema = ToolSchema(
                properties = JsonObject(
                    mapOf(
                        "title" to JsonPrimitive("string"),
                        "description" to JsonPrimitive("string"),
                        "triggerAt" to JsonPrimitive("string"),
                        "timezone" to JsonPrimitive("string"),
                        "metadata" to JsonPrimitive("string"),
                    )
                ),
                required = listOf("title", "triggerAt")
            ),
            title = null,
            outputSchema = ToolSchema(
                properties = JsonObject(
                    mapOf(
                        "id" to JsonPrimitive("string"),
                        "triggerAt" to JsonPrimitive("string"),
                    )
                ),
                required = listOf("id", "triggerAt")
            ),
            toolAnnotations = null,
            meta = null,
        ) { request ->
            handleCreateReminder(service, request.arguments)
        }

        // Register reminder.list tool
        server.addTool(
            name = "reminder.list",
            description = "List reminders with optional filters (status, time range, limit).",
            inputSchema = ToolSchema(
                properties = JsonObject(
                    mapOf(
                        "status" to JsonPrimitive("string"),
                        "from" to JsonPrimitive("string"),
                        "to" to JsonPrimitive("string"),
                        "limit" to JsonPrimitive("integer"),
                    )
                ),
                required = null
            ),
            title = null,
            outputSchema = ToolSchema(
                properties = JsonObject(
                    mapOf(
                        "reminders" to JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("array"),
                                "items" to JsonObject(
                                    mapOf(
                                        "type" to JsonPrimitive("object"),
                                        "properties" to JsonObject(
                                            mapOf(
                                                "id" to JsonPrimitive("string"),
                                                "title" to JsonPrimitive("string"),
                                                "description" to JsonPrimitive("string"),
                                                "triggerAt" to JsonPrimitive("string"),
                                                "timezone" to JsonPrimitive("string"),
                                                "createdAt" to JsonPrimitive("string"),
                                                "status" to JsonPrimitive("string"),
                                                "metadata" to JsonPrimitive("string"),
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                ),
                required = listOf("reminders")
            ),
            toolAnnotations = null,
            meta = null,
        ) { request ->
            handleListReminders(service, request.arguments)
        }

        // Register reminder.cancel tool
        server.addTool(
            name = "reminder.cancel",
            description = "Cancel a reminder by ID. Marks the reminder as CANCELLED and prevents queued events from being delivered.",
            inputSchema = ToolSchema(
                properties = JsonObject(
                    mapOf(
                        "id" to JsonPrimitive("string"),
                    )
                ),
                required = listOf("id")
            ),
            title = null,
            outputSchema = ToolSchema(
                properties = JsonObject(
                    mapOf(
                        "type" to JsonPrimitive("string"),
                    )
                ),
                required = null
            ),
            toolAnnotations = null,
            meta = null,
        ) { request ->
            handleCancelReminder(service, request.arguments)
        }

        // Register reminder.claimDue tool
        server.addTool(
            name = "reminder.claimDue",
            description = "Atomically claim due reminder events. Returns events that are due and haven't been sent yet. " +
                    "Use this to poll for due events without calling the LLM. Only call the LLM when events are returned.",
            inputSchema = ToolSchema(
                properties = JsonObject(
                    mapOf(
                        "now" to JsonPrimitive("string"),
                        "limit" to JsonPrimitive("integer"),
                        "claimerId" to JsonPrimitive("string"),
                    )
                ),
                required = listOf("claimerId")
            ),
            title = null,
            outputSchema = ToolSchema(
                properties = JsonObject(
                    mapOf(
                        "claimToken" to JsonPrimitive("string"),
                        "events" to JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("array"),
                                "items" to JsonObject(
                                    mapOf(
                                        "type" to JsonPrimitive("object"),
                                        "properties" to JsonObject(
                                            mapOf(
                                                "eventId" to JsonPrimitive("string"),
                                                "reminderId" to JsonPrimitive("string"),
                                                "title" to JsonPrimitive("string"),
                                                "description" to JsonPrimitive("string"),
                                                "triggerAt" to JsonPrimitive("string"),
                                                "timezone" to JsonPrimitive("string"),
                                                "claimToken" to JsonPrimitive("string"),
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                ),
                required = listOf("claimToken", "events")
            ),
            toolAnnotations = null,
            meta = null,
        ) { request ->
            handleClaimDue(service, request.arguments)
        }

        // Register reminder.ackSent tool
        server.addTool(
            name = "reminder.ackSent",
            description = "Acknowledge that reminder events were successfully sent. Call this after the LLM has processed the events.",
            inputSchema = ToolSchema(
                properties = JsonObject(
                    mapOf(
                        "claimToken" to JsonPrimitive("string"),
                        "eventIds" to JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("array"),
                                "items" to JsonObject(
                                    mapOf("type" to JsonPrimitive("string"))
                                )
                            )
                        ),
                        "sentAt" to JsonPrimitive("string"),
                    )
                ),
                required = listOf("claimToken", "eventIds")
            ),
            title = null,
            outputSchema = ToolSchema(
                properties = JsonObject(
                    mapOf(
                        "acknowledged" to JsonPrimitive("integer"),
                    )
                ),
                required = listOf("acknowledged")
            ),
            toolAnnotations = null,
            meta = null,
        ) { request ->
            handleAckSent(service, request.arguments)
        }

        // Register reminder.fail tool
        server.addTool(
            name = "reminder.fail",
            description = "Mark reminder events as failed. Increments attempt count and stores error message. " +
                    "After 3 attempts, the claim is released so the event can be reclaimed.",
            inputSchema = ToolSchema(
                properties = JsonObject(
                    mapOf(
                        "claimToken" to JsonPrimitive("string"),
                        "eventIds" to JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("array"),
                                "items" to JsonObject(
                                    mapOf("type" to JsonPrimitive("string"))
                                )
                            )
                        ),
                        "error" to JsonPrimitive("string"),
                    )
                ),
                required = listOf("claimToken", "eventIds", "error")
            ),
            title = null,
            outputSchema = ToolSchema(
                properties = JsonObject(
                    mapOf(
                        "failed" to JsonPrimitive("integer"),
                    )
                ),
                required = listOf("failed")
            ),
            toolAnnotations = null,
            meta = null,
        ) { request ->
            handleFail(service, request.arguments)
        }

        logger.info("Reminder module installed successfully")
    }
}

