package io.modelcontextprotocol.sample.server.pokeapi

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

/**
 * Base URL for PokeAPI v2.
 */
private const val POKEAPI_BASE_URL = "https://pokeapi.co/api/v2"

/**
 * Default timeout for HTTP requests in milliseconds.
 */
private const val DEFAULT_TIMEOUT_MS = 10_000L

/**
 * Implementation of [PokeApiClient] using Ktor HttpClient.
 *
 * @param httpClient The Ktor HttpClient instance to use for requests.
 * @param baseUrl The base URL for PokeAPI (defaults to the public PokeAPI v2 endpoint).
 */
class PokeApiClientImpl(
    private val httpClient: HttpClient,
    private val baseUrl: String = POKEAPI_BASE_URL,
) : PokeApiClient {
    override suspend fun getPokemonByNameOrId(nameOrId: String): PokemonSummary {
        val normalized = nameOrId.lowercase().trim()
        val url = "$baseUrl/pokemon/$normalized"
        httpLogger.debug("getPokemonByNameOrId called with nameOrId: '$nameOrId', normalized: '$normalized', URL: '$url'")
        return try {
            val result = get<PokemonSummary>(url)
            httpLogger.debug("getPokemonByNameOrId succeeded for: '$normalized'")
            result
        } catch (e: Exception) {
            httpLogger.error("getPokemonByNameOrId failed for: '$normalized', URL: '$url'", e)
            throw e
        }
    }

    override suspend fun getMoveByNameOrId(nameOrId: String): MoveSummary {
        val normalized = nameOrId.lowercase().trim()
        return get<MoveSummary>("$baseUrl/move/$normalized")
    }

    override suspend fun searchPokemon(limit: Int, offset: Int): PokemonListResponse {
        val actualLimit = limit.coerceIn(1, 100)
        val actualOffset = offset.coerceAtLeast(0)
        return get<PokemonListResponse>("$baseUrl/pokemon?limit=$actualLimit&offset=$actualOffset")
    }

    /**
     * Performs a GET request and handles the response.
     */
    private suspend inline fun <reified T> get(url: String): T {
        httpLogger.debug("HTTP GET request to: $url")
        return try {
            val response: HttpResponse = httpClient.get(url)
            httpLogger.debug("HTTP GET response status: ${response.status}, URL: $url")
            if (response.status == HttpStatusCode.OK) {
                val body = response.body<T>()
                httpLogger.debug("HTTP GET response body parsed successfully for: $url")
                return body
            }
            
            // Handle error status codes
            val errorMessage = try {
                response.bodyAsText().take(200)
            } catch (e: Exception) {
                null
            }
            
            httpLogger.warn("HTTP GET error response - Status: ${response.status}, URL: $url, Error message: $errorMessage")
            
            when {
                response.status == HttpStatusCode.NotFound -> {
                    httpLogger.debug("Resource not found: $url")
                    throw PokeApiError.NotFound("resource", url.substringAfterLast("/"))
                }
                response.status.value in 400..499 -> {
                    httpLogger.error("Client error (4xx) - Status: ${response.status.value}, URL: $url, Message: $errorMessage")
                    throw PokeApiError.ClientError(
                        statusCode = response.status.value,
                        errorMessage = errorMessage,
                    )
                }
                response.status.value in 500..599 -> {
                    throw PokeApiError.ServerError(
                        statusCode = response.status.value,
                        errorMessage = errorMessage,
                    )
                }
                else -> {
                    throw PokeApiError.ServerError(
                        statusCode = response.status.value,
                        errorMessage = "Unexpected status code",
                    )
                }
            }
        } catch (e: PokeApiError) {
            throw e
        } catch (e: TimeoutCancellationException) {
            throw PokeApiError.NetworkError(e)
        } catch (e: TimeoutException) {
            throw PokeApiError.NetworkError(e)
        } catch (e: SocketTimeoutException) {
            throw PokeApiError.NetworkError(e)
        } catch (e: ConnectException) {
            throw PokeApiError.NetworkError(e)
        } catch (e: Exception) {
            // Wrap any other exception as a network error
            throw PokeApiError.NetworkError(e)
        }
    }
}

private val httpLogger = LoggerFactory.getLogger("PokeApiHttpClient")

/**
 * Creates a configured HttpClient for PokeAPI requests.
 */
fun createPokeApiHttpClient(): HttpClient {
    return io.ktor.client.HttpClient(io.ktor.client.engine.cio.CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = DEFAULT_TIMEOUT_MS
            connectTimeoutMillis = DEFAULT_TIMEOUT_MS
            socketTimeoutMillis = DEFAULT_TIMEOUT_MS
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    httpLogger.debug(message)
                }
            }
            level = LogLevel.ALL
        }
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }
            )
        }
    }
}

