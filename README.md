# MCP Kotlin Server Sample

A sample implementation of an MCP (Model Context Protocol) server in Kotlin that demonstrates different server
configurations and transport methods.

## Features

- Multiple server operation modes: 
    - Standard I/O server
    - SSE (Server-Sent Events) server with plain configuration
    - SSE server using Ktor plugin
- Built-in capabilities for:
    - Prompts management
    - Resources handling
    - Tools integration

## Getting Started

### Running the Server

The server defaults [STDIO transport](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports#stdio). 

You can customize the behavior using command-line arguments.
Logs are printed to [./build/stdout.log](./build/stdout.log)

#### Standard I/O mode (STDIO):

```bash
./gradlew clean build
```
Use the [MCP inspector](https://modelcontextprotocol.io/docs/tools/inspector) 
to connect to MCP via STDIO (Click the "▶️ Connect" button):

```shell
npx @modelcontextprotocol/inspector --config mcp-inspector-config.json --server stdio-server
```

#### SSE with plain configuration:

**NB!: 🐞 This configuration may not work ATM**

```bash
./gradlew run --args="--sse-server 3001"
```
or
```shell
./gradlew clean build
java -jar ./build/libs/kotlin-mcp-server-0.1.0-all.jar --sse-server 3001
```

Use the [MCP inspector](https://modelcontextprotocol.io/docs/tools/inspector) 
to connect to `http://localhost:3002/` via SSE Transport (Click the "▶️ Connect" button):
```shell
npx @modelcontextprotocol/inspector --config mcp-inspector-config.json --server sse-server
```

#### SSE with Ktor plugin:

```bash
./gradlew run --args="--sse-server-ktor 3002"
```
or
```shell
./gradlew clean build
java -jar ./build/libs/kotlin-mcp-server-0.1.0-all.jar --sse-server-ktor 3002
```

Use the [MCP inspector](https://modelcontextprotocol.io/docs/tools/inspector) 
to connect to `http://localhost:3002/` via SSE transport (Click the "▶️ Connect" button):
```shell
npx @modelcontextprotocol/inspector --config mcp-inspector-config.json --server sse-ktor-server
```

## Server Capabilities

- **Prompts**: Supports prompt management with list change notifications
- **Resources**: Includes subscription support and list change notifications
- **Tools**: Supports tool management with list change notifications

## Implementation Details

The server is implemented using:
- Ktor for HTTP server functionality (SSE modes)
- Kotlin coroutines for asynchronous operations
- SSE for real-time communication in web contexts
- Standard I/O for command-line interface and process-based communication

## Example Capabilities

The sample server demonstrates:
- **Prompt**: "Kotlin Developer" - helps develop small Kotlin applications with a configurable project name
- **Resource**: "Web Search" - a placeholder resource demonstrating resource handling

## PokeAPI Tools

The server includes three MCP tools that interact with the public [PokeAPI v2](https://pokeapi.co/) REST API:

### `pokeapi_get_pokemon`

Fetches a summarized view of a Pokémon by name or ID.

**Arguments:**
- `name` (string, required): Pokémon name (case-insensitive) or numeric ID
- `includeRawJson` (boolean, optional, default: false): If true, includes the raw JSON response from PokeAPI

**Returns:**
- Pokémon ID, name, height, weight, types, and sprite URLs
- Optionally includes raw JSON when `includeRawJson` is true

**Example:**
```json
{
  "name": "pikachu",
  "includeRawJson": false
}
```

### `pokeapi_get_move`

Fetches basic information about a Pokémon move by name or ID.

**Arguments:**
- `idOrName` (string, required): Move name (case-insensitive) or numeric ID

**Returns:**
- Move ID, name, power, PP, accuracy, type, and damage class

**Example:**
```json
{
  "idOrName": "thunderbolt"
}
```

### `pokeapi_search_pokemon`

Lists Pokémon names using the paginated list endpoint.

**Arguments:**
- `limit` (integer, optional, default: 20, max: 100): Maximum number of results to return
- `offset` (integer, optional, default: 0): Number of results to skip

**Returns:**
- Total count, pagination info (next/previous URLs), and list of Pokémon names with URLs

**Example:**
```json
{
  "limit": 10,
  "offset": 0
}
```

### Features

- **Caching**: All Pokémon and move lookups are cached in-memory for 5 minutes to reduce API calls
- **Error Handling**: Clear error messages for not found (404), network errors, and server errors
- **Idiomatic Kotlin**: Uses coroutines, suspend functions, and structured concurrency
- **Type Safety**: Sealed error types and null-safe data models

### Implementation Details

The PokeAPI integration uses:
- Ktor HTTP client with JSON serialization (kotlinx.serialization)
- In-memory cache with TTL (5 minutes) and size limits (100 entries)
- Thread-safe and coroutine-safe caching implementation
- Robust error handling with domain-specific error types

## Reminder Tools

The server includes a complete reminder/scheduling subsystem backed by SQLite. The reminder system supports creating reminders, polling for due events, and acknowledging delivery. It's designed for 24/7 operation with safe restarts, no duplicates, and concurrent poller support.

### Database

Reminders are stored in a SQLite database (default: `./reminders.db`). The database schema is automatically migrated on server startup. The schema includes:

- **reminders** table: Stores reminder metadata (title, description, trigger time, status, etc.)
- **reminder_events** table: Queue-like table for event delivery with claim tokens to prevent duplicates

### `reminder.create`

Creates a new reminder/event. Stores the reminder and creates a queued event for delivery.

**Arguments:**
- `title` (string, required): Short title for the reminder
- `description` (string, optional): Detailed description
- `triggerAt` (string, required): RFC3339/ISO-8601 timestamp (e.g., "2024-01-15T14:30:00Z")
- `timezone` (string, optional): Timezone for display purposes
- `metadata` (string, optional): JSON string for extensibility

**Returns:**
- `id` (string): Created reminder ID
- `triggerAt` (string): Normalized trigger time in UTC

**Example:**
```json
{
  "title": "Team Meeting",
  "description": "Weekly sync with the team",
  "triggerAt": "2024-01-15T14:30:00Z",
  "timezone": "America/New_York"
}
```

### `reminder.list`

Lists reminders with optional filters.

**Arguments:**
- `status` (string, optional): Filter by status ("PENDING", "CANCELLED", "COMPLETED")
- `from` (string, optional): RFC3339/ISO-8601 timestamp - filter reminders with triggerAt >= from
- `to` (string, optional): RFC3339/ISO-8601 timestamp - filter reminders with triggerAt <= to
- `limit` (integer, optional): Maximum number of results

**Returns:**
- `reminders` (array): List of reminder objects with id, title, description, triggerAt, timezone, createdAt, status, metadata

**Example:**
```json
{
  "status": "PENDING",
  "from": "2024-01-01T00:00:00Z",
  "to": "2024-01-31T23:59:59Z",
  "limit": 10
}
```

### `reminder.cancel`

Cancels a reminder by ID. Marks the reminder as CANCELLED and prevents queued events from being delivered.

**Arguments:**
- `id` (string, required): Reminder ID to cancel

**Returns:**
- Success message

**Example:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000"
}
```

### `reminder.claimDue`

Atomically claims due reminder events. This is the polling endpoint - call this to check for due events without invoking the LLM. Only call the LLM when events are returned.

**Arguments:**
- `claimerId` (string, required): Identifier for the claiming process/client
- `now` (string, optional): RFC3339/ISO-8601 timestamp override for testing (defaults to current time)
- `limit` (integer, optional, default: 10): Maximum number of events to claim

**Returns:**
- `claimToken` (string): Token to use when acknowledging or failing events
- `events` (array): List of claimed events with eventId, reminderId, title, description, triggerAt, timezone, claimToken

**Example:**
```json
{
  "claimerId": "client-1",
  "limit": 10
}
```

### `reminder.ackSent`

Acknowledges that reminder events were successfully sent. Call this after the LLM has processed the events.

**Arguments:**
- `claimToken` (string, required): Token from the claimDue response
- `eventIds` (array of strings, required): List of event IDs that were successfully sent
- `sentAt` (string, optional): RFC3339/ISO-8601 timestamp override (defaults to current time)

**Returns:**
- `acknowledged` (integer): Number of events acknowledged

**Example:**
```json
{
  "claimToken": "abc123...",
  "eventIds": ["event-id-1", "event-id-2"]
}
```

### `reminder.fail`

Marks reminder events as failed. Increments attempt count and stores error message. After 3 attempts, the claim is released so the event can be reclaimed.

**Arguments:**
- `claimToken` (string, required): Token from the claimDue response
- `eventIds` (array of strings, required): List of event IDs that failed
- `error` (string, required): Error message describing the failure

**Returns:**
- `failed` (integer): Number of events marked as failed

**Example:**
```json
{
  "claimToken": "abc123...",
  "eventIds": ["event-id-1"],
  "error": "LLM processing failed: timeout"
}
```

### Expected Polling Flow

The reminder system is designed for external clients to poll for due events:

1. **Poll for due events**: External client calls `reminder.claimDue` periodically (e.g., every minute)
2. **Process if events found**: If `reminder.claimDue` returns events:
   - Call the LLM with the event details
   - After LLM processes successfully, call `reminder.ackSent` with the claimToken and eventIds
3. **Handle failures**: If processing fails, call `reminder.fail` with the error message
4. **Repeat**: Continue polling with `reminder.claimDue`

**Key Features:**
- **No duplicates**: Atomic claiming ensures multiple pollers don't process the same event
- **Claim timeout**: Claims expire after 5 minutes (configurable), allowing failed processes to be reclaimed
- **Retry logic**: Events can be retried up to 3 times before being released
- **Safe restarts**: Database persistence ensures reminders survive server restarts

### Implementation Details

The reminder subsystem uses:
- SQLite for persistent storage with automatic schema migrations
- Atomic transactions for claim operations to prevent race conditions
- Injectable clock for testability
- Encapsulated package structure (`io.modelcontextprotocol.sample.server.reminder`)
- Minimal integration point: single `ReminderModule.install()` call in server startup
