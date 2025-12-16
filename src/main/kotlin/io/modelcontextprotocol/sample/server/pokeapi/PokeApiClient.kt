package io.modelcontextprotocol.sample.server.pokeapi

/**
 * Client interface for interacting with the PokeAPI v2 REST API.
 *
 * All methods are suspending functions that should be called from a coroutine context.
 * Errors are represented as [PokeApiError] exceptions.
 */
interface PokeApiClient {
    /**
     * Fetches a Pokémon by name or ID.
     *
     * @param nameOrId The Pokémon name (case-insensitive) or numeric ID.
     * @return A [PokemonSummary] with the Pokémon's details.
     * @throws PokeApiError.NotFound if the Pokémon doesn't exist.
     * @throws PokeApiError.NetworkError if a network error occurs.
     * @throws PokeApiError.ServerError if the API returns a server error.
     */
    suspend fun getPokemonByNameOrId(nameOrId: String): PokemonSummary

    /**
     * Fetches a move by name or ID.
     *
     * @param nameOrId The move name (case-insensitive) or numeric ID.
     * @return A [MoveSummary] with the move's details.
     * @throws PokeApiError.NotFound if the move doesn't exist.
     * @throws PokeApiError.NetworkError if a network error occurs.
     * @throws PokeApiError.ServerError if the API returns a server error.
     */
    suspend fun getMoveByNameOrId(nameOrId: String): MoveSummary

    /**
     * Searches for Pokémon using pagination.
     *
     * @param limit Maximum number of results to return (default: 20, max: 100).
     * @param offset Number of results to skip (default: 0).
     * @return A [PokemonListResponse] with the paginated results.
     * @throws PokeApiError.NetworkError if a network error occurs.
     * @throws PokeApiError.ServerError if the API returns a server error.
     */
    suspend fun searchPokemon(limit: Int = 20, offset: Int = 0): PokemonListResponse
}

