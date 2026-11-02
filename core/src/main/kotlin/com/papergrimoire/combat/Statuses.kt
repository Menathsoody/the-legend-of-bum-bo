package com.papergrimoire.combat

import com.papergrimoire.board.TileType

/** Status effects that can be attached to enemies or the player. */
enum class StatusKind {
    STUN,        // skips the owner's next action
    BLEED,       // takes X damage at start of its turn (stackable)
    POISON,      // loses damage-per-stack each turn, stacks decay
    SHIELD_BREAK,// takes +50% damage from puzzle matches
    VULNERABLE,  // +% damage taken (value = percent)
    WEAKENED,    // -% damage dealt (value = percent)
    CHARGE_UP,   // telegraphed heavy attack incoming (value = damage)
    HEAL_BLOCK,  // cannot receive healing for N turns
    ARMOR,       // flat damage reduction per hit (player only, decays)
    THORNS,      // reflects damage when attacked physically
    HASTE,       // extra moves / reduced cooldowns
    DOOM,        // takes big damage after N turns (boss mechanic)
}

data class StatusEffect(
    val kind: StatusKind,
    var stacks: Int = 1,
    var value: Int = 1,
    var turns: Int = 1,
)

/** Central place where every combat number flows through — used by board resolution,
 *  spells, items and enemy AI alike. Keeps effects data-driven instead of scattered. */
sealed class GameEvent {
    data class MatchResolved(val family: TileType, val size: Int, val cascade: Int) : GameEvent()
    data class DamageDealtToEnemy(val targetIndex: Int, val amount: Int, val source: String) : GameEvent()
    data class DamageTakenByPlayer(val amount: Int, val source: String) : GameEvent()
    data class ManaGained(val type: TileType, val amount: Int) : GameEvent()
    data class SpellCast(val spellId: String) : GameEvent()
    data class EnemyKilled(val enemyId: String) : GameEvent()
    object TurnEnded : GameEvent()
    object TurnStarted : GameEvent()
    data class HeartPopped(val count: Int) : GameEvent()
    data class CurseDestroyed(val count: Int) : GameEvent()
}
