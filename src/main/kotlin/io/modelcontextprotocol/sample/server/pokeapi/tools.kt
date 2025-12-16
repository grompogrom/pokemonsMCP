package io.modelcontextprotocol.sample.server.pokeapi

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * Arguments for the pokeapi_get_pokemon tool.
 */
@Serializable
data class GetPokemonArgs(
    val name: String,
    val includeRawJson: Boolean = false,
)

/**
 * Arguments for the pokeapi_get_move tool.
 */
@Serializable
data class GetMoveArgs(
    val idOrName: String,
)

/**
 * Arguments for the pokeapi_search_pokemon tool.
 */
@Serializable
data class SearchPokemonArgs(
    val limit: Int = 20,
    val offset: Int = 0,
)

private val logger = LoggerFactory.getLogger("PokeApiTools")

/**
 * Handles the pokeapi_get_pokemon tool call.
 */
suspend fun handleGetPokemon(
    pokeApiClient: PokeApiClient,
    arguments: Map<String, Any?>?,
): CallToolResult {
    logger.debug("=== handleGetPokemon called ===")
    logger.debug("arguments: $arguments")
    logger.debug("arguments type: ${arguments?.javaClass?.name}")
    logger.debug("arguments == null: ${arguments == null}")
    
    if (arguments == null) {
        logger.warn("Arguments is null, returning error")
        return CallToolResult(
            content = listOf(TextContent("Error: Missing arguments")),
            isError = true,
        )
    }

    logger.debug("arguments.keys: ${arguments.keys}")
    logger.debug("arguments['name']: ${arguments["name"]}")
    logger.debug("arguments['name'] type: ${arguments["name"]?.javaClass?.name}")
    
    // Extract name properly handling both Map and JsonObject
    val name = when (val nameValue = arguments["name"]) {
        is String -> nameValue.trim()
        is JsonElement -> nameValue.jsonPrimitive.content.trim()
        else -> nameValue?.toString()?.trim()?.removeSurrounding("\"") ?: ""
    }
    logger.debug("extracted name: '$name'")
    logger.debug("name.isNullOrBlank(): ${name.isNullOrBlank()}")
    
    if (name.isNullOrBlank()) {
        logger.warn("Name is null or blank, returning error. Arguments were: $arguments")
        return CallToolResult(
            content = listOf(TextContent("Error: 'name' argument is required")),
            isError = true,
        )
    }

    // Extract includeRawJson properly handling both Map and JsonObject
    val includeRawJson = when (val rawJsonValue = arguments["includeRawJson"]) {
        is Boolean -> rawJsonValue
        is JsonElement -> {
            try {
                val primitive = rawJsonValue.jsonPrimitive
                if (primitive.isString) {
                    primitive.content.toBooleanStrictOrNull() ?: false
                } else {
                    primitive.content.toBooleanStrictOrNull() ?: false
                }
            } catch (e: Exception) {
                false
            }
        }
        else -> (rawJsonValue as? Boolean) ?: false
    }
    logger.debug("includeRawJson: $includeRawJson")
    logger.debug("Calling pokeApiClient.getPokemonByNameOrId with name: '$name'")

    return try {
        val pokemon = pokeApiClient.getPokemonByNameOrId(name)
        logger.debug("Successfully retrieved pokemon: ${pokemon.name} (ID: ${pokemon.id})")
        val typeNames = pokemon.getTypeNames()

        val result = buildString {
            appendLine("Pokémon: ${pokemon.name} (ID: ${pokemon.id})")
            appendLine("Height: ${pokemon.height} decimetres")
            appendLine("Weight: ${pokemon.weight} hectograms")
            appendLine("Types: ${typeNames.joinToString(", ")}")
            if (pokemon.sprites.frontDefault != null) {
                appendLine("Front Sprite: ${pokemon.sprites.frontDefault}")
            }
            if (pokemon.sprites.frontShiny != null) {
                appendLine("Shiny Sprite: ${pokemon.sprites.frontShiny}")
            }
        }

        val content = mutableListOf<TextContent>(
            TextContent(result),
        )

        // Include raw JSON if requested
        if (includeRawJson) {
            val json = Json { prettyPrint = true }
            val rawJson = json.encodeToString(
                PokemonSummary.serializer(),
                pokemon,
            )
            content.add(TextContent("\nRaw JSON:\n$rawJson"))
        }

        CallToolResult(content = content)
    } catch (e: PokeApiError.NotFound) {
        logger.error("PokeApiError.NotFound: ${e.message}", e)
        CallToolResult(
            content = listOf(TextContent("Error: ${e.message}")),
            isError = true,
        )
    } catch (e: PokeApiError) {
        logger.error("PokeApiError: ${e.message}", e)
        CallToolResult(
            content = listOf(TextContent("Error: ${e.message}")),
            isError = true,
        )
    } catch (e: Exception) {
        logger.error("Unexpected error: ${e.message}", e)
        CallToolResult(
            content = listOf(TextContent("Error: Unexpected error: ${e.message}")),
            isError = true,
        )
    }
}

/**
 * Handles the pokeapi_get_move tool call.
 */
suspend fun handleGetMove(
    pokeApiClient: PokeApiClient,
    arguments: Map<String, Any?>?,
): CallToolResult {
    if (arguments == null) {
        return CallToolResult(
            content = listOf(TextContent("Error: Missing arguments")),
            isError = true,
        )
    }

    // Extract idOrName properly handling both Map and JsonObject
    val idOrName = when (val idOrNameValue = arguments["idOrName"]) {
        is String -> idOrNameValue.trim()
        is JsonElement -> idOrNameValue.jsonPrimitive.content.trim()
        else -> idOrNameValue?.toString()?.trim()?.removeSurrounding("\"") ?: ""
    }
    if (idOrName.isNullOrBlank()) {
        return CallToolResult(
            content = listOf(TextContent("Error: 'idOrName' argument is required")),
            isError = true,
        )
    }

    return try {
        val move = pokeApiClient.getMoveByNameOrId(idOrName)

        val result = buildString {
            appendLine("Move: ${move.name} (ID: ${move.id})")
            if (move.power != null) {
                appendLine("Power: ${move.power}")
            }
            if (move.pp != null) {
                appendLine("PP: ${move.pp}")
            }
            if (move.accuracy != null) {
                appendLine("Accuracy: ${move.accuracy}%")
            }
            if (move.type != null) {
                appendLine("Type: ${move.type.name}")
            }
            if (move.damageClass != null) {
                appendLine("Damage Class: ${move.damageClass.name}")
            }
        }

        CallToolResult(content = listOf(TextContent(result)))
    } catch (e: PokeApiError.NotFound) {
        CallToolResult(
            content = listOf(TextContent("Error: ${e.message}")),
            isError = true,
        )
    } catch (e: PokeApiError) {
        CallToolResult(
            content = listOf(TextContent("Error: ${e.message}")),
            isError = true,
        )
    } catch (e: Exception) {
        CallToolResult(
            content = listOf(TextContent("Error: Unexpected error: ${e.message}")),
            isError = true,
        )
    }
}

/**
 * Handles the pokeapi_search_pokemon tool call.
 */
suspend fun handleSearchPokemon(
    pokeApiClient: PokeApiClient,
    arguments: Map<String, Any?>?,
): CallToolResult {
    // Extract limit properly handling both Map and JsonObject
    val limit = when (val limitValue = arguments?.get("limit")) {
        is Number -> limitValue.toInt()
        is JsonElement -> {
            try {
                limitValue.jsonPrimitive.content.toIntOrNull() ?: 20
            } catch (e: Exception) {
                20
            }
        }
        is String -> limitValue.toIntOrNull() ?: 20
        else -> 20
    }
    
    // Extract offset properly handling both Map and JsonObject
    val offset = when (val offsetValue = arguments?.get("offset")) {
        is Number -> offsetValue.toInt()
        is JsonElement -> {
            try {
                offsetValue.jsonPrimitive.content.toIntOrNull() ?: 0
            } catch (e: Exception) {
                0
            }
        }
        is String -> offsetValue.toIntOrNull() ?: 0
        else -> 0
    }

    val actualLimit = limit.coerceIn(1, 100)
    val actualOffset = offset.coerceAtLeast(0)

    return try {
        val response = pokeApiClient.searchPokemon(actualLimit, actualOffset)

        val result = buildString {
            appendLine("Found ${response.count} Pokémon total")
            appendLine("Showing ${response.results.size} results (offset: $actualOffset, limit: $actualLimit)")
            if (response.next != null) {
                appendLine("Next page available")
            }
            if (response.previous != null) {
                appendLine("Previous page available")
            }
            appendLine()
            appendLine("Results:")
            response.results.forEachIndexed { index, item ->
                appendLine("${index + 1}. ${item.name} (${item.url})")
            }
        }

        CallToolResult(content = listOf(TextContent(result)))
    } catch (e: PokeApiError) {
        CallToolResult(
            content = listOf(TextContent("Error: ${e.message}")),
            isError = true,
        )
    } catch (e: Exception) {
        CallToolResult(
            content = listOf(TextContent("Error: Unexpected error: ${e.message}")),
            isError = true,
        )
    }
}

