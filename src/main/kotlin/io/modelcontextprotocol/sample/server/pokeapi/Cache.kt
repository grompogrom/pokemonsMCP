package io.modelcontextprotocol.sample.server.pokeapi

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Configuration for the PokeAPI cache.
 */
data class CacheConfig(
    /**
     * Time-to-live for cached entries. Default: 5 minutes.
     */
    val ttl: Duration = 5.minutes,
    /**
     * Maximum number of entries in the cache. Default: 100.
     */
    val maxSize: Int = 100,
    /**
     * Whether caching is enabled. Default: true.
     */
    val enabled: Boolean = true,
)

/**
 * A simple in-memory cache with TTL and size limits.
 * Thread-safe and coroutine-safe.
 */
class InMemoryCache<T>(
    private val config: CacheConfig = CacheConfig(),
) {
    private data class CacheEntry<T>(
        val value: T,
        val expiresAt: Long,
    )

    private val cache = mutableMapOf<String, CacheEntry<T>>()
    private val mutex = Mutex()

    /**
     * Gets a value from the cache, or null if not found or expired.
     */
    suspend fun get(key: String): T? = mutex.withLock {
        val entry = cache[key] ?: return null
        if (entry.expiresAt < System.currentTimeMillis()) {
            cache.remove(key)
            return null
        }
        entry.value
    }

    /**
     * Puts a value in the cache.
     */
    suspend fun put(key: String, value: T) {
        if (!config.enabled) return
        mutex.withLock {
            // Remove expired entries first
            val now = System.currentTimeMillis()
            cache.entries.removeAll { it.value.expiresAt < now }

            // Enforce size limit by removing oldest entries (simple FIFO)
            while (cache.size >= config.maxSize) {
                val oldestKey = cache.keys.firstOrNull()
                if (oldestKey != null) {
                    cache.remove(oldestKey)
                } else {
                    break
                }
            }

            val expiresAt = System.currentTimeMillis() + config.ttl.inWholeMilliseconds
            cache[key] = CacheEntry(value, expiresAt)
        }
    }

    /**
     * Clears all entries from the cache.
     */
    suspend fun clear() = mutex.withLock {
        cache.clear()
    }

    /**
     * Gets the current size of the cache.
     */
    suspend fun size(): Int = mutex.withLock {
        cache.size
    }
}

/**
 * Wraps a [PokeApiClient] with caching.
 */
class CachedPokeApiClient(
    private val delegate: PokeApiClient,
    private val config: CacheConfig = CacheConfig(),
) : PokeApiClient {
    private val pokemonCache = InMemoryCache<PokemonSummary>(config)
    private val moveCache = InMemoryCache<MoveSummary>(config)

    override suspend fun getPokemonByNameOrId(nameOrId: String): PokemonSummary {
        val key = "pokemon:${nameOrId.lowercase().trim()}"
        return pokemonCache.get(key) ?: run {
            val result = delegate.getPokemonByNameOrId(nameOrId)
            pokemonCache.put(key, result)
            result
        }
    }

    override suspend fun getMoveByNameOrId(nameOrId: String): MoveSummary {
        val key = "move:${nameOrId.lowercase().trim()}"
        return moveCache.get(key) ?: run {
            val result = delegate.getMoveByNameOrId(nameOrId)
            moveCache.put(key, result)
            result
        }
    }

    override suspend fun searchPokemon(limit: Int, offset: Int): PokemonListResponse {
        // Don't cache search results as they may change
        return delegate.searchPokemon(limit, offset)
    }
}

