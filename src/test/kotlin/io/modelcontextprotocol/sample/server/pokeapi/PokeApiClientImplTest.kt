package io.modelcontextprotocol.sample.server.pokeapi

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class PokeApiClientImplTest {
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
                json(this@PokeApiClientImplTest.json)
            }
        }
    }

    @Test
    fun `getPokemonByNameOrId returns PokemonSummary on success`() = runTest {
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

        val result = client.getPokemonByNameOrId("bulbasaur")

        assertEquals(1, result.id)
        assertEquals("bulbasaur", result.name)
        assertEquals(7, result.height)
        assertEquals(69, result.weight)
        assertEquals(2, result.types.size)
        assertNotNull(result.sprites.frontDefault)
    }

    @Test
    fun `getPokemonByNameOrId throws NotFound on 404`() = runTest {
        val httpClient = createMockHttpClient(HttpStatusCode.NotFound, "Not Found")
        val client = PokeApiClientImpl(httpClient)

        assertFailsWith<PokeApiError.NotFound> {
            client.getPokemonByNameOrId("nonexistent")
        }
    }

    @Test
    fun `getMoveByNameOrId returns MoveSummary on success`() = runTest {
        val moveJson = """
            {
                "id": 1,
                "name": "pound",
                "power": 40,
                "pp": 35,
                "accuracy": 100,
                "type": {"name": "normal"},
                "damage_class": {"name": "physical"}
            }
        """.trimIndent()

        val httpClient = createMockHttpClient(HttpStatusCode.OK, moveJson)
        val client = PokeApiClientImpl(httpClient)

        val result = client.getMoveByNameOrId("pound")

        assertEquals(1, result.id)
        assertEquals("pound", result.name)
        assertEquals(40, result.power)
        assertEquals(35, result.pp)
        assertEquals(100, result.accuracy)
        assertNotNull(result.type)
        assertEquals("normal", result.type?.name)
    }

    @Test
    fun `searchPokemon returns PokemonListResponse on success`() = runTest {
        val listJson = """
            {
                "count": 1154,
                "next": "https://pokeapi.co/api/v2/pokemon?offset=20&limit=20",
                "previous": null,
                "results": [
                    {"name": "bulbasaur", "url": "https://pokeapi.co/api/v2/pokemon/1/"},
                    {"name": "ivysaur", "url": "https://pokeapi.co/api/v2/pokemon/2/"}
                ]
            }
        """.trimIndent()

        val httpClient = createMockHttpClient(HttpStatusCode.OK, listJson)
        val client = PokeApiClientImpl(httpClient)

        val result = client.searchPokemon(limit = 20, offset = 0)

        assertEquals(1154, result.count)
        assertNotNull(result.next)
        assertEquals(2, result.results.size)
        assertEquals("bulbasaur", result.results[0].name)
    }
}

