package io.modelcontextprotocol.sample.server.reminder

import kotlinx.coroutines.test.runTest
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderServiceTest {
    private lateinit var db: ReminderDatabase
    private lateinit var repository: ReminderRepository
    private lateinit var service: ReminderService
    private lateinit var clock: Clock
    private val dbPath = "./test_reminders.db"

    @BeforeTest
    fun setUp() {
        // Use a fixed clock for testing
        clock = Clock.fixed(Instant.parse("2024-01-01T12:00:00Z"), ZoneId.of("UTC"))
        
        // Clean up any existing test database
        File(dbPath).delete()
        
        db = ReminderDatabase(dbPath, clock)
        repository = ReminderRepository(db.getConnection())
        service = ReminderService(repository, clock)
    }

    @AfterTest
    fun tearDown() {
        db.close()
        File(dbPath).delete()
    }

    @Test
    fun `test create reminder`() = runTest {
        val input = CreateReminderInput(
            title = "Test Reminder",
            description = "Test Description",
            triggerAt = "2024-01-02T12:00:00Z",
            timezone = "UTC",
        )

        val output = service.createReminder(input)

        assertNotNull(output.id)
        assertEquals("2024-01-02T12:00:00Z", output.triggerAt)

        val reminders = service.listReminders(ListRemindersInput())
        assertEquals(1, reminders.reminders.size)
        assertEquals("Test Reminder", reminders.reminders[0].title)
    }

    @Test
    fun `test create reminder with invalid triggerAt`() = runTest {
        val input = CreateReminderInput(
            title = "Test",
            triggerAt = "invalid-date",
        )

        try {
            service.createReminder(input)
            assertTrue(false, "Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected
            assertNotNull(e)
        }
    }

    @Test
    fun `test list reminders with filters`() = runTest {
        // Create multiple reminders
        service.createReminder(CreateReminderInput(
            title = "Past",
            triggerAt = "2023-12-01T12:00:00Z",
        ))
        service.createReminder(CreateReminderInput(
            title = "Future",
            triggerAt = "2024-02-01T12:00:00Z",
        ))

        // List all
        val all = service.listReminders(ListRemindersInput())
        assertEquals(2, all.reminders.size)

        // Filter by status
        val pending = service.listReminders(ListRemindersInput(status = "PENDING"))
        assertEquals(2, pending.reminders.size)

        // Filter by time range
        val inRange = service.listReminders(ListRemindersInput(
            from = "2024-01-01T00:00:00Z",
            to = "2024-01-31T23:59:59Z",
        ))
        assertEquals(1, inRange.reminders.size)
        assertEquals("Past", inRange.reminders[0].title)
    }

    @Test
    fun `test cancel reminder`() = runTest {
        val created = service.createReminder(CreateReminderInput(
            title = "To Cancel",
            triggerAt = "2024-01-02T12:00:00Z",
        ))

        val cancelled = service.cancelReminder(created.id)
        assertTrue(cancelled)

        val reminders = service.listReminders(ListRemindersInput(status = "CANCELLED"))
        assertEquals(1, reminders.reminders.size)
    }

    @Test
    fun `test claim due events`() = runTest {
        // Create a reminder in the past
        val past = service.createReminder(CreateReminderInput(
            title = "Past Reminder",
            triggerAt = "2024-01-01T11:00:00Z", // Before clock time
        ))

        // Create a reminder in the future
        service.createReminder(CreateReminderInput(
            title = "Future Reminder",
            triggerAt = "2024-01-02T12:00:00Z",
        ))

        // Claim due events
        val claimed = service.claimDueEvents(ClaimDueInput(
            claimerId = "test-claimer",
            limit = 10,
        ))

        assertEquals(1, claimed.events.size)
        assertEquals("Past Reminder", claimed.events[0].title)
        assertNotNull(claimed.claimToken)
        assertFalse(claimed.claimToken.isEmpty())
    }

    @Test
    fun `test claim due events with no due events`() = runTest {
        // Create only future reminders
        service.createReminder(CreateReminderInput(
            title = "Future",
            triggerAt = "2024-01-02T12:00:00Z",
        ))

        val claimed = service.claimDueEvents(ClaimDueInput(
            claimerId = "test-claimer",
        ))

        assertEquals(0, claimed.events.size)
    }

    @Test
    fun `test acknowledge sent`() = runTest {
        // Create and claim
        service.createReminder(CreateReminderInput(
            title = "Test",
            triggerAt = "2024-01-01T11:00:00Z",
        ))

        val claimed = service.claimDueEvents(ClaimDueInput(
            claimerId = "test-claimer",
        ))

        assertEquals(1, claimed.events.size)
        val eventId = claimed.events[0].eventId

        // Acknowledge
        val ack = service.acknowledgeSent(AckSentInput(
            claimToken = claimed.claimToken,
            eventIds = listOf(eventId),
        ))

        assertEquals(1, ack.acknowledged)

        // Try to claim again - should be empty
        val claimedAgain = service.claimDueEvents(ClaimDueInput(
            claimerId = "test-claimer",
        ))
        assertEquals(0, claimedAgain.events.size)
    }

    @Test
    fun `test full flow create claim ack`() = runTest {
        // Create
        val created = service.createReminder(CreateReminderInput(
            title = "Full Flow Test",
            description = "Testing the full flow",
            triggerAt = "2024-01-01T11:00:00Z",
        ))

        // Claim
        val claimed = service.claimDueEvents(ClaimDueInput(
            claimerId = "flow-test",
        ))

        assertEquals(1, claimed.events.size)
        assertEquals("Full Flow Test", claimed.events[0].title)

        // Ack
        val ack = service.acknowledgeSent(AckSentInput(
            claimToken = claimed.claimToken,
            eventIds = claimed.events.map { it.eventId },
        ))

        assertEquals(1, ack.acknowledged)
    }

    @Test
    fun `test mark failed`() = runTest {
        // Create and claim
        service.createReminder(CreateReminderInput(
            title = "Fail Test",
            triggerAt = "2024-01-01T11:00:00Z",
        ))

        val claimed = service.claimDueEvents(ClaimDueInput(
            claimerId = "test",
        ))

        assertEquals(1, claimed.events.size)

        // Mark as failed
        val failed = service.markFailed(FailInput(
            claimToken = claimed.claimToken,
            eventIds = claimed.events.map { it.eventId },
            error = "Test error",
        ))

        assertEquals(1, failed.failed)

        // After 3 failures, should be reclaimable
        // (We'd need to mark it failed 2 more times, but for now just verify it was marked)
    }
}

