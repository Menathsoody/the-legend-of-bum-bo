package com.papergrimoire.board

/** The five base mana families and the special tile kinds. */
enum class TileType {
    SHARD,      // Осколки  — attack energy (red)
    CLAY,       // Глина    — defense energy (green)
    SPORE,      // Споры    — control energy (purple)
    CRYSTAL,    // Кристалл — heavy damage energy (blue)
    SPARK,      // Искры    — extra actions / recharge (yellow)
    HEART,      // special: instant healing tile
    CURSE,      // special: harmful tile, cannot match, blocks lines
    WILD,       // special: matches with any base family
    SEALED;     // special: hidden content, revealed when matched adjacent or by effects

    val isBase: Boolean get() = this == SHARD || this == CLAY || this == SPORE || this == CRYSTAL || this == SPARK

    companion object {
        val BASE = listOf(SHARD, CLAY, SPORE, CRYSTAL, SPARK)
    }
}

/**
 * A single board cell. Immutable value semantics are used everywhere; the board mutates
 * by replacing entries. [sealed] cells display "?" and count as their real type only when
 * resolved.
 */
data class Tile(
    val id: Int,               // unique instance id (for animation bookkeeping)
    val type: TileType,
    val sealed: Boolean = false,
    val marked: Boolean = false // boss/AI "cursed mark": destroyed at end of player turn
) {
    fun unseal(newType: TileType): Tile = copy(type = newType, sealed = false)
}
