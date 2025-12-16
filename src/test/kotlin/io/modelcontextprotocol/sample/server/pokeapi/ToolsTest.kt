package io.modelcontextprotocol.sample.server.pokeapi

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

class ToolsTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun createMockHttpClient(
        statusCode: HttpStatusCode,
        body: String,
    ): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = body,
                        status = statusCode,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
            install(ContentNegotiation) {
                json(this@ToolsTest.json)
            }
        }
    }

    @Test
    fun `handleGetPokemon returns error when arguments is null`() = runTest {
        val httpClient = createMockHttpClient(HttpStatusCode.OK, "{}")
        val client = PokeApiClientImpl(httpClient)

        val result = handleGetPokemon(client, null)

        assertTrue(result.isError == true)
        val firstContent = result.content.firstOrNull() as? TextContent
        assertTrue(firstContent != null)
        assertTrue(firstContent?.text?.contains("Missing arguments") == true)
    }

    @Test
    fun `handleGetPokemon returns error when name is missing`() = runTest {
        val httpClient = createMockHttpClient(HttpStatusCode.OK, "{}")
        val client = PokeApiClientImpl(httpClient)

        val result = handleGetPokemon(client, emptyMap())

        assertTrue(result.isError == true)
        val firstContent = result.content.firstOrNull() as? TextContent
        assertTrue(firstContent != null)
        assertTrue(firstContent?.text?.contains("'name' argument is required") == true)
    }

    @Test
    fun `handleGetPokemon returns error when name is blank`() = runTest {
        val httpClient = createMockHttpClient(HttpStatusCode.OK, "{}")
        val client = PokeApiClientImpl(httpClient)

        val result = handleGetPokemon(client, mapOf("name" to "   "))

        assertTrue(result.isError == true)
        val firstContent = result.content.firstOrNull() as? TextContent
        assertTrue(firstContent != null)
        assertTrue(firstContent?.text?.contains("'name' argument is required") == true)
    }

    @Test
    fun `handleGetPokemon succeeds with valid name in arguments`() = runTest {
        val pokemonJson = """
            {
                "id": 1,
                "name": "bulbasaur",
                "height": 7,
                "weight": 69,
                "types": [
                    {"type": {"name": "grass"}},
                    {"type": {"name": "poison"}}
                ],
                "sprites": {
                    "front_default": "https://example.com/bulbasaur.png"
                }
            }
        """.trimIndent()

        val httpClient = createMockHttpClient(HttpStatusCode.OK, pokemonJson)
        val client = PokeApiClientImpl(httpClient)

        val result = handleGetPokemon(client, mapOf("name" to "bulbasaur"))

        assertTrue(result.isError != true)
        assertTrue(result.content.isNotEmpty())
        val firstContent = result.content.firstOrNull() as? TextContent
        assertTrue(firstContent != null)
        assertTrue(firstContent?.text?.contains("bulbasaur") == true)
    }

    @Test
    fun `handleGetPokemon succeeds with name from meta-like map`() = runTest {
        val pokemonJson = """
            {
                "id": 1,
                "name": "cheri",
                "height": 7,
                "weight": 69,
                "types": [
                    {"type": {"name": "grass"}},
                    {"type": {"name": "poison"}}
                ],
                "sprites": {
                    "front_default": "https://example.com/cheri.png"
                }
            }
        """.trimIndent()

        val httpClient = createMockHttpClient(HttpStatusCode.OK, pokemonJson)
        val client = PokeApiClientImpl(httpClient)

        // Simulate the case where arguments is empty but name is in a meta-like map
        val metaLikeMap = mapOf("progressToken" to 15, "name" to "cheri")
        val result = handleGetPokemon(client, metaLikeMap)

        assertTrue(result.isError != true)
        assertTrue(result.content.isNotEmpty())
        val firstContent = result.content.firstOrNull() as? TextContent
        assertTrue(firstContent != null)
        assertTrue(firstContent?.text?.contains("cheri") == true)
    }

    @Test
    fun `handleGetPokemon handles empty arguments with name in alternative location`() = runTest {
        val pokemonJson = """
            {
                "id": 1,
                "name": "pikachu",
                "height": 4,
                "weight": 60,
                "types": [
                    {"type": {"name": "electric"}}
                ],
                "sprites": {
                    "front_default": "https://example.com/pikachu.png"
                }
            }
        """.trimIndent()

        val httpClient = createMockHttpClient(HttpStatusCode.OK, pokemonJson)
        val client = PokeApiClientImpl(httpClient)

        // Test with empty arguments map but name in the map (simulating meta.json fallback)
        val argsWithName = mapOf<String, Any?>("name" to "pikachu")
        val result = handleGetPokemon(client, argsWithName)

        assertTrue(result.isError != true)
        assertTrue(result.content.isNotEmpty())
        val firstContent = result.content.firstOrNull() as? TextContent
        assertTrue(firstContent != null)
        assertTrue(firstContent?.text?.contains("pikachu") == true)
    }
}

