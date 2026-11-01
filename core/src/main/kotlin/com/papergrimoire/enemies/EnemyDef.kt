package com.papergrimoire.enemies

import com.papergrimoire.board.TileType
import com.papergrimoire.combat.StatusKind

/** One entry in an enemy's telegraphed action script. */
sealed class EnemyAction {
    data class Attack(val damage: Int, val statusKind: StatusKind? = null, val statusValue: Int = 0) : EnemyAction()
    data class HeavyAttack(val damage: Int) : EnemyAction()           // telegraphed one turn ahead
    data object Defend : EnemyAction()                                // gains shield
    data class GainShield(val amount: Int) : EnemyAction()
    data class ApplyToPlayer(val kind: StatusKind, val value: Int, val turns: Int) : EnemyAction()
    data class BoardCurse(val count: Int) : EnemyAction()             // adds curse tiles
    data object BoardSeal : EnemyAction()                             // seals random tiles
    data object BoardMark : EnemyAction()                             // marks tiles to pop
    data class ShiftBoardRow(val row: Int, val amount: Int) : EnemyAction()
    data class Summon(val enemyId: String) : EnemyAction()
    data class BuffAlly(val percent: Int) : EnemyAction()             // allies deal +% dmg
    data class HealSelf(val amount: Int) : EnemyAction()
    data class Steal(val coins: Int) : EnemyAction()
    data class PoisonCloud(val stacks: Int) : EnemyAction()
    data object SleepMist : EnemyAction()                             // may stun player next turn (heal-block-ish control)
    data class ConvertTiles(val to: TileType, val count: Int) : EnemyAction()
    data object DoNothing : EnemyAction()
}

/** Static definition of an enemy or boss. Data-driven catalogue entry. */
data class EnemyDef(
    val id: String,
    val name: String,
    val hp: Int,
    val isBoss: Boolean = false,
    val areaIds: List<String>,                    // which areas it spawns in
    val moveWeight: Int = 1,                      // spawn weight inside its areas
    /** The AI cycles through this script; special picks override via [special]. */
    val script: List<EnemyAction>,
    /** chance % to deviate from the scripted cycle into a random weighted pick */
    val unpredictability: Int = 0,
    val goldMin: Int = 2,
    val goldMax: Int = 6,
    val colorHex: Long = 0x888888FF,
    /** Boss-only: phase thresholds as fraction of HP where script restarts with new list */
    val phases: List<List<EnemyAction>>? = null,
    val flavor: String = "",
) {
    fun rollGold(rng: com.papergrimoire.util.SeededRng): Int =
        if (goldMax >= goldMin) rng.nextInt(goldMin, goldMax + 1) else goldMin
}
