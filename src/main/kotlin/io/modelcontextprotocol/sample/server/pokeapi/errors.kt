package io.modelcontextprotocol.sample.server.pokeapi

/**
 * Sealed hierarchy of errors that can occur when calling PokeAPI.
 */
sealed class PokeApiError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /**
     * The requested resource was not found (HTTP 404).
     */
    data class NotFound(
        val resource: String,
        val identifier: String,
    ) : PokeApiError("Pokémon or resource '$identifier' not found in $resource")

    /**
     * A network error occurred (timeout, connection failure, etc.).
     */
    data class NetworkError(
        override val cause: Throwable?,
    ) : PokeApiError("Network error: ${cause?.message ?: "Unknown error"}", cause)

    /**
     * The PokeAPI server returned an error (HTTP 5xx).
     */
    data class ServerError(
        val statusCode: Int,
        val errorMessage: String? = null,
    ) : PokeApiError("PokeAPI server error (HTTP $statusCode): ${errorMessage ?: "Unknown error"}")

    /**
     * The request was invalid (HTTP 4xx, excluding 404).
     */
    data class ClientError(
        val statusCode: Int,
        val errorMessage: String? = null,
    ) : PokeApiError("Invalid request (HTTP $statusCode): ${errorMessage ?: "Unknown error"}")
}

