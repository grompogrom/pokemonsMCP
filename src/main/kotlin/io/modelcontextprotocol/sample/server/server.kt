package io.modelcontextprotocol.sample.server

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.util.collections.ConcurrentMap
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.types.PromptMessage
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Job
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import io.modelcontextprotocol.sample.server.pokeapi.CachedPokeApiClient
import io.modelcontextprotocol.sample.server.pokeapi.PokeApiClientImpl
import io.modelcontextprotocol.sample.server.pokeapi.createPokeApiHttpClient
import io.modelcontextprotocol.sample.server.pokeapi.handleGetPokemon
import io.modelcontextprotocol.sample.server.pokeapi.handleGetMove
import io.modelcontextprotocol.sample.server.pokeapi.handleSearchPokemon
import io.ktor.client.HttpClient

// Shared HttpClient instance for PokeAPI requests
private val pokeApiHttpClient: HttpClient = createPokeApiHttpClient()
private val pokeApiClient = CachedPokeApiClient(
    PokeApiClientImpl(pokeApiHttpClient),
)

fun configureServer(): Server {
    val server = Server(
        Implementation(
            name = "mcp-kotlin test server",
            version = "0.1.0",
        ),
        ServerOptions(
            capabilities = ServerCapabilities(
                prompts = ServerCapabilities.Prompts(listChanged = true),
                resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
                tools = ServerCapabilities.Tools(listChanged = true),
            ),
        ),
    )

    server.addPrompt(
        name = "Kotlin Developer",
        description = "Develop small kotlin applications",
        arguments = listOf(
            PromptArgument(
                name = "Project Name",
                description = "Project name for the new project",
                required = true,
            ),
        ),
    ) { request ->
        GetPromptResult(
            messages = listOf(
                PromptMessage(
                    role = Role.User,
                    content = TextContent(
                        "Develop a kotlin project named <name>${request.arguments?.get("Project Name")}</name>",
                    ),
                ),
            ),
            description = "Description for ${request.name}",
        )
    }

    // Add PokeAPI tools
    server.addTool(
        name = "pokeapi_get_pokemon",
        description = "Fetch a summarized view of a Pokémon by name or ID from PokeAPI. Returns id, name, height, weight, types, and sprite URLs.",
        inputSchema = ToolSchema(
            properties = JsonObject(
                mapOf(
                    "name" to JsonPrimitive("string"),
                )
            ),
            required = listOf("name")
        ),
        title = null,
        outputSchema = ToolSchema(
            properties = JsonObject(
                mapOf(
                    "id" to JsonPrimitive("integer"),
                    "name" to JsonPrimitive("string"),
                    "height" to JsonPrimitive("integer"),
                    "weight" to JsonPrimitive("integer"),
                    "types" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("array"),
                            "items" to JsonObject(
                                mapOf("type" to JsonPrimitive("string"))
                            )
                        )
                    ),
                    "frontSprite" to JsonPrimitive("string"),
                    "shinySprite" to JsonPrimitive("string"),
                )
            ),
            required = listOf("id", "name", "height", "weight", "types")
        ),
        toolAnnotations = null,
        meta = null
    ) { request ->
        // Use arguments, but merge with meta.json if arguments is empty or missing 'name'
        val arguments = request.arguments ?: emptyMap()
        val metaJson = request.meta?.json as? Map<String, Any?>
        
        val args = if (arguments.isEmpty() || !arguments.containsKey("name")) {
            // If arguments is empty or missing name, try to get it from meta.json
            metaJson ?: arguments
        } else {
            arguments
        }
        handleGetPokemon(pokeApiClient, args)
    }

    server.addTool(
        name = "pokeapi_get_move",
        description = "Fetch basic information about a Pokémon move by name or ID from PokeAPI. Returns id, name, power, PP, accuracy, type, and damage class.",
        inputSchema = ToolSchema(
            properties = JsonObject(
                mapOf(
                    "idOrName" to JsonPrimitive("string"),
                )
            ),
            required = listOf("idOrName")
        ),
        title = null,
        outputSchema = ToolSchema(
            properties = JsonObject(
                mapOf(
                    "id" to JsonPrimitive("integer"),
                    "name" to JsonPrimitive("string"),
                    "power" to JsonPrimitive("integer"),
                    "pp" to JsonPrimitive("integer"),
                    "accuracy" to JsonPrimitive("integer"),
                    "type" to JsonPrimitive("string"),
                    "damageClass" to JsonPrimitive("string"),
                )
            ),
            required = listOf("id", "name")
        ),
        toolAnnotations = null,
        meta = null
    ) { request ->
        // Use arguments, but merge with meta.json if arguments is empty or missing 'idOrName'
        val arguments = request.arguments ?: emptyMap()
        val metaJson = request.meta?.json as? Map<String, Any?>
        
        val args = if (arguments.isEmpty() || !arguments.containsKey("idOrName")) {
            // If arguments is empty or missing idOrName, try to get it from meta.json
            metaJson ?: arguments
        } else {
            arguments
        }
        handleGetMove(pokeApiClient, args)
    }

    server.addTool(
        name = "pokeapi_search_pokemon",
        description = "List Pokémon names using the paginated list endpoint from PokeAPI. Supports limit (1-100, default 20) and offset (default 0) parameters.",
        inputSchema = ToolSchema(
            properties = JsonObject(
                mapOf(
                    "limit" to JsonPrimitive("integer"),
                    "offset" to JsonPrimitive("integer"),
                )
            ),
            required = null
        ),
        title = null,
        outputSchema = ToolSchema(
            properties = JsonObject(
                mapOf(
                    "count" to JsonPrimitive("integer"),
                    "results" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("array"),
                            "items" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("object"),
                                    "properties" to JsonObject(
                                        mapOf(
                                            "name" to JsonPrimitive("string"),
                                            "url" to JsonPrimitive("string"),
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    "next" to JsonPrimitive("string"),
                    "previous" to JsonPrimitive("string"),
                )
            ),
            required = listOf("count", "results")
        ),
        toolAnnotations = null,
        meta = null
    ) { request ->
        // Use arguments, but merge with meta.json if arguments is empty
        val arguments = request.arguments ?: emptyMap()
        val metaJson = request.meta?.json as? Map<String, Any?>
        
        val args = if (arguments.isEmpty()) {
            // If arguments is empty, try to get it from meta.json
            metaJson ?: arguments
        } else {
            arguments
        }
        handleSearchPokemon(pokeApiClient, args)
    }

    // Add a resource
    server.addResource(
        uri = "https://search.com/",
        name = "Web Search",
        description = "Web search engine",
        mimeType = "text/html",
    ) { request ->
        ReadResourceResult(
            contents = listOf(
                TextResourceContents("Placeholder content for ${request.uri}", request.uri, "text/html"),
            ),
        )
    }

    return server
}

fun runSseMcpServerWithPlainConfiguration(port: Int, wait: Boolean = true): EmbeddedServer<*, *> {
    printBanner(port = port, path = "/sse")
    val serverSessions = ConcurrentMap<String, ServerSession>()

    val server = configureServer()

    val ktorServer = embeddedServer(CIO, host = "127.0.0.1", port = port) {
        installCors()
        install(SSE)
        routing {
            sse("/sse") {
                val transport = SseServerTransport("/message", this)
                val serverSession = server.createSession(transport)
                serverSessions[transport.sessionId] = serverSession

                serverSession.onClose {
                    println("Server session closed for: ${transport.sessionId}")
                    serverSessions.remove(transport.sessionId)
                }
                awaitCancellation()
            }
            post("/message") {
                val sessionId: String? = call.request.queryParameters["sessionId"]
                if (sessionId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing sessionId parameter")
                    return@post
                }

                val transport = serverSessions[sessionId]?.transport as? SseServerTransport
                if (transport == null) {
                    call.respond(HttpStatusCode.NotFound, "Session not found")
                    return@post
                }

                transport.handlePostMessage(call)
            }
        }
    }.start(wait = wait)

    return ktorServer
}

/**
 * Starts an SSE (Server-Sent Events) MCP server using the Ktor plugin.
 *
 * This is the recommended approach for SSE servers as it simplifies configuration.
 * The URL can be accessed in the MCP inspector at http://localhost:[port]/sse
 *
 * @param port The port number on which the SSE MCP server will listen for client connections.
 */
fun runSseMcpServerUsingKtorPlugin(port: Int, wait: Boolean = true): EmbeddedServer<*, *> {
    printBanner(port)

     val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
        installCors()
        mcp {
            return@mcp configureServer()
        }
    }.start(wait = wait)
    return server
}

private fun printBanner(port: Int, path: String = "") {
    if (port == 0) {
        println("🎬 Starting SSE server on random port")
    } else {
        println("🎬 Starting SSE server on ${if (port > 0) "port $port" else "random port"}")
        println("🔍 Use MCP inspector to connect to http://localhost:$port$path")
    }
}

private fun Application.installCors() {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Delete)
        allowNonSimpleContentTypes = true
        anyHost()
    }
}

/**
 * Starts an MCP server using Standard I/O transport.
 *
 * This mode is useful for process-based communication where the server
 * communicates via stdin/stdout with a parent process or client.
 */
fun runMcpServerUsingStdio() {
    val server = configureServer()
    val transport = StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = System.out.asSink().buffered(),
    )

    runBlocking {
        server.createSession(transport)
        val done = Job()
        server.onClose {
            done.complete()
        }
        done.join()
    }
}
