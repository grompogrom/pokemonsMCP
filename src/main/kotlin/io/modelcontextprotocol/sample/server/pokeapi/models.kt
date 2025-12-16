package io.modelcontextprotocol.sample.server.pokeapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Summary of a Pokémon from PokeAPI.
 */
@Serializable
data class PokemonSummary(
    val id: Int,
    val name: String,
    val height: Int,
    val weight: Int,
    val types: List<TypeSlot>,
    val sprites: Sprites,
) {
    /**
     * Returns a list of type names.
     */
    fun getTypeNames(): List<String> = types.map { it.type.name }
}

@Serializable
data class TypeSlot(
    val type: TypeInfo,
)

@Serializable
data class TypeInfo(
    val name: String,
)

@Serializable
data class Sprites(
    @SerialName("front_default") val frontDefault: String? = null,
    @SerialName("front_shiny") val frontShiny: String? = null,
    @SerialName("back_default") val backDefault: String? = null,
    @SerialName("back_shiny") val backShiny: String? = null,
)

/**
 * Summary of a move from PokeAPI.
 */
@Serializable
data class MoveSummary(
    val id: Int,
    val name: String,
    val power: Int? = null,
    val pp: Int? = null,
    val accuracy: Int? = null,
    val type: TypeInfo? = null,
    @SerialName("damage_class") val damageClass: DamageClass? = null,
)

@Serializable
data class DamageClass(
    val name: String,
)

/**
 * Paginated list of Pokémon from PokeAPI.
 */
@Serializable
data class PokemonListResponse(
    val count: Int,
    val next: String? = null,
    val previous: String? = null,
    val results: List<PokemonListItem>,
)

@Serializable
data class PokemonListItem(
    val name: String,
    val url: String,
)

