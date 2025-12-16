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
