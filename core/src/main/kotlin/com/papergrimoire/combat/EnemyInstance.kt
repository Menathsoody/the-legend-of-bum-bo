package com.papergrimoire.combat

import com.papergrimoire.enemies.EnemyAction
import com.papergrimoire.enemies.EnemyDef
import com.papergrimoire.save.Saveable

/** Runtime enemy instance inside a battle. */
class EnemyInstance(
    val def: EnemyDef,
    var hp: Int = def.hp,
    var shield: Int = 0,
    /** index into the current script (cycle) */
    var scriptIndex: Int = 0,
    var phase: Int = 0,
    /** heavy attacks telegraphed: stored damage to land next turn */
    var telegraphedHeavy: Int = 0,
    val statuses: MutableList<StatusEffect> = mutableListOf(),
    var buffPercent: Int = 0,        // from ally buffers
    var alive: Boolean = true,
) : Saveable {

    val maxHp: Int get() = if (def.phases != null) def.hp else def.hp

    fun hasStatus(kind: StatusKind) = statuses.any { it.kind == kind }
    fun statusValue(kind: StatusKind): Int = statuses.filter { it.kind == kind }.sumOf { it.value * it.stacks }

    fun addStatus(kind: StatusKind, stacks: Int = 1, value: Int = 1, turns: Int = 1) {
        val ex = statuses.firstOrNull { it.kind == kind }
        if (ex != null) { ex.stacks += stacks; ex.value = maxOf(ex.value, value); ex.turns = maxOf(ex.turns, turns) }
        else statuses.add(StatusEffect(kind, stacks, value, turns))
    }

    fun takeDamage(amount: Int): Int {
        var dmg = amount
        if (hasStatus(StatusKind.SHIELD_BREAK)) dmg = dmg * 3 / 2
        if (hasStatus(StatusKind.VULNERABLE)) dmg = dmg * (100 + statusValue(StatusKind.VULNERABLE)) / 100
        if (shield > 0) {
            val abs = minOf(shield, dmg); shield -= abs; dmg -= abs
        }
        hp = (hp - dmg).coerceAtLeast(0)
        if (hp == 0) alive = false
        return dmg
    }

    fun heal(amount: Int) { hp = (hp + amount).coerceAtMost(def.hp) }

    /** Current script considering boss phases. */
    fun currentScript(): List<EnemyAction> =
        if (def.phases != null && phase in def.phases.indices) def.phases[phase] else def.script

    /** Boss phase transitions based on HP fraction. Returns true if phase changed. */
    fun updatePhase(): Boolean {
        if (!def.isBoss || def.phases == null) return false
        val frac = hp.toFloat() / def.hp
        val target = when {
            frac > 0.66f -> 0
            frac > 0.33f -> minOf(1, def.phases.size - 1)
            else -> def.phases.size - 1
        }
        return if (target != phase) {
            phase = target
            scriptIndex = 0 // restart cycle with new phase script
            true
        } else false
    }

    /** Pick this enemy's next action deterministically from state + rng. */
    fun chooseAction(rng: com.papergrimoire.util.SeededRng): EnemyAction {
        val script = currentScript()
        if (script.isEmpty()) return EnemyAction.DoNothing
        // stun consumes the action
        val stunIdx = statuses.indexOfFirst { it.kind == StatusKind.STUN }
        if (stunIdx >= 0) {
            val s = statuses[stunIdx]; s.turns--
            if (s.turns <= 0) statuses.removeAt(stunIdx)
            return EnemyAction.DoNothing
        }
        // poison/bleed ticks
        val poison = statusValue(StatusKind.POISON)
        if (poison > 0) { hp = (hp - poison).coerceAtLeast(0); if (hp == 0) alive = false }
        val bleed = statusValue(StatusKind.BLEED)
        if (bleed > 0) { hp = (hp - bleed).coerceAtLeast(0); if (hp == 0) alive = false }
        // decay statuses
        val it = statuses.iterator()
        while (it.hasNext()) { val s = it.next(); s.turns--; if (s.turns <= 0) it.remove() }

        if (!alive) return EnemyAction.DoNothing

        // low-HP self preservation override for healers/defensive units
        if (hp < def.hp / 4 && script.any { it is EnemyAction.HealSelf } && rng.nextInt(100) < 50) {
            return script.first { it is EnemyAction.HealSelf }
        }

        val roll = rng.nextInt(100)
        val action = if (roll < def.unpredictability) {
            rng.pick(script)
        } else {
            val a = script[scriptIndex % script.size]
            scriptIndex++
            a
        }
        // never pick impossible actions
        return when (action) {
            is EnemyAction.Summon -> if (action.enemyId.isBlank()) EnemyAction.DoNothing else action
            else -> action
        }
    }

    override fun saveToString(): String = buildString {
        append(def.id).append('|').append(hp).append('|').append(shield).append('|')
        append(scriptIndex).append('|').append(phase).append('|').append(telegraphedHeavy).append('|')
        append(buffPercent).append('|').append(if (alive) 1 else 0).append('|')
        append(statuses.joinToString(";") { "${it.kind},${it.stacks},${it.value},${it.turns}" })
    }

    companion object {
        fun loadFromString(data: String): EnemyInstance? {
            val p = data.split("|")
            val def = com.papergrimoire.enemies.EnemyCatalogue.byId(p[0]) ?: return null
            val e = EnemyInstance(def)
            e.hp = p[1].toInt(); e.shield = p[2].toInt(); e.scriptIndex = p[3].toInt()
            e.phase = p[4].toInt(); e.telegraphedHeavy = p[5].toInt(); e.buffPercent = p[6].toInt()
            e.alive = p[7].toInt() == 1
            if (p.size > 8 && p[8].isNotBlank()) for (s in p[8].split(";")) {
                val f = s.split(","); e.statuses.add(StatusEffect(StatusKind.valueOf(f[0]), f[1].toInt(), f[2].toInt(), f[3].toInt()))
            }
            return e
        }
    }
}
