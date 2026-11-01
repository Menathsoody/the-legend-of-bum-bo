package com.papergrimoire.combat

import com.papergrimoire.board.TileType
import com.papergrimoire.characters.CharacterDef
import com.papergrimoire.save.Saveable

/** Runtime player state inside a run. All numbers here are mutable combat state; the
 *  static definition lives in [CharacterDef]. Serialisable for mid-run saves. */
class PlayerState(
    val def: CharacterDef,
    var hp: Int = def.maxHp,
    var maxHpBonus: Int = 0,
    var shield: Int = 0,
    var movesLeft: Int = def.baseMoves,
    var extraMovesNextTurn: Int = 0,
    var coins: Int = 0,
    val mana: EnumMap<TileType, Int> = EnumMap(TileType::class.java).also {
        for (t in TileType.BASE) it[t] = 0
    },
    val statuses: MutableList<StatusEffect> = mutableListOf(),
    /** spell id -> remaining cooldown turns */
    val cooldowns: MutableMap<String, Int> = mutableMapOf(),
    /** active consumable inventory: item id -> charges */
    val items: MutableMap<String, Int> = linkedMapOf(),
    /** passive artefacts collected this run */
    val artifacts: MutableList<String> = mutableListOf(),
    /** learned spells this run (ids) */
    var spells: MutableList<String> = mutableListOf(),
    var puzzleDamageBonus: Int = 0,   // flat added to every match effect
    var shardHitsBonus: Int = 0,      // extra hits per shard match
    var clayBonusPct: Int = 0,        // % more shield from clay
    var sporeBonusPct: Int = 0,       // % longer control effects
    var crystalBonusPct: Int = 0,
    var sparkBonusPct: Int = 0,
    var manaGainPct: Int = 0,
    var luckBonus: Int = 0,
    var thorns: Int = 0,
    var armorPerTurn: Int = 0,
    var lifestealPct: Int = 0,
    var moveCostReduction: Int = 0,   // reserved for future artifacts
    var turnCount: Int = 0,
) : Saveable {

    val maxHp: Int get() = def.maxHp + maxHpBonus
    fun mana(t: TileType): Int = mana.getOrDefault(t, 0)
    fun addMana(t: TileType, amount: Int) {
        if (!t.isBase) return
        mana[t] = (mana(t) + amount).coerceAtLeast(0)
    }
    fun spendMana(t: TileType, amount: Int): Boolean {
        if (mana(t) < amount) return false
        mana[t] = mana(t) - amount
        return true
    }
    fun hasStatus(kind: StatusKind) = statuses.any { it.kind == kind }
    fun statusValue(kind: StatusKind): Int = statuses.filter { it.kind == kind }.sumOf { it.value * it.stacks }
    fun addStatus(kind: StatusKind, stacks: Int = 1, value: Int = 1, turns: Int = 1) {
        val ex = statuses.firstOrNull { it.kind == kind }
        if (ex != null) { ex.stacks += stacks; ex.value = maxOf(ex.value, value); ex.turns = maxOf(ex.turns, turns) }
        else statuses.add(StatusEffect(kind, stacks, value, turns))
    }

    /** Apply incoming damage to shield first, then hp. Returns hp damage actually dealt. */
    fun takeDamage(amount: Int): Int {
        var dmg = amount
        val armor = statusValue(StatusKind.ARMOR)
        dmg = (dmg - armor).coerceAtLeast(0)
        if (shield > 0) {
            val absorbed = minOf(shield, dmg)
            shield -= absorbed
            dmg -= absorbed
        }
        hp = (hp - dmg).coerceAtLeast(0)
        return dmg
    }

    fun heal(amount: Int): Int {
        if (hasStatus(StatusKind.HEAL_BLOCK)) return 0
        val before = hp
        hp = (hp + amount).coerceAtMost(maxHp)
        return hp - before
    }

    /** Decay per-turn timers; returns list of expired statuses for UI. */
    fun tickStatuses(): List<StatusEffect> {
        val expired = ArrayList<StatusEffect>()
        val it = statuses.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.turns--
            if (s.turns <= 0) { expired.add(s); it.remove() }
        }
        // bleed ticks on player too
        val bleed = statusValue(StatusKind.BLEED)
        if (bleed > 0) hp = (hp - bleed).coerceAtLeast(0)
        return expired
    }

    override fun saveToString(): String = buildString {
        append(def.id).append('|').append(hp).append('|').append(maxHpBonus).append('|')
        append(shield).append('|').append(movesLeft).append('|').append(extraMovesNextTurn).append('|')
        append(coins).append('|').append(turnCount).append('|')
        append(TileType.BASE.joinToString(",") { mana(it).toString() }).append('|')
        append(statuses.joinToString(";") { "${it.kind},${it.stacks},${it.value},${it.turns}" }).append('|')
        append(cooldowns.entries.joinToString(";") { "${it.key}=${it.value}" }).append('|')
        append(items.entries.joinToString(";") { "${it.key}:${it.value}" }).append('|')
        append(artifacts.joinToString(",")).append('|')
        append(spells.joinToString(",")).append('|')
        append(puzzleDamageBonus).append('|').append(shardHitsBonus).append('|').append(clayBonusPct)
        append('|').append(sporeBonusPct).append('|').append(crystalBonusPct).append('|').append(sparkBonusPct)
        append('|').append(manaGainPct).append('|').append(luckBonus).append('|').append(thorns)
        append('|').append(armorPerTurn).append('|').append(lifestealPct).append('|').append(moveCostReduction)
    }

    companion object {
        fun loadFromString(data: String): PlayerState {
            val p = data.split("|")
            val def = CharacterCatalogueById.get(p[0])
            val ps = PlayerState(def)
            ps.hp = p[1].toInt(); ps.maxHpBonus = p[2].toInt(); ps.shield = p[3].toInt()
            ps.movesLeft = p[4].toInt(); ps.extraMovesNextTurn = p[5].toInt(); ps.coins = p[6].toInt()
            ps.turnCount = p[7].toInt()
            val manaVals = p[8].split(",")
            TileType.BASE.forEachIndexed { i, t -> ps.mana[t] = manaVals.getOrNull(i)?.toIntOrNull() ?: 0 }
            if (p[9].isNotBlank()) for (s in p[9].split(";")) {
                val f = s.split(","); ps.statuses.add(StatusEffect(StatusKind.valueOf(f[0]), f[1].toInt(), f[2].toInt(), f[3].toInt()))
            }
            if (p[10].isNotBlank()) for (e in p[10].split(";")) {
                val f = e.split("="); ps.cooldowns[f[0]] = f[1].toInt()
            }
            if (p[11].isNotBlank()) for (e in p[11].split(";")) {
                val f = e.split(":"); ps.items[f[0]] = f[1].toInt()
            }
            if (p[12].isNotBlank()) ps.artifacts.addAll(p[12].split(","))
            if (p[13].isNotBlank()) ps.spells.addAll(p[13].split(","))
            fun gi(i: Int) = p.getOrNull(i)?.toIntOrNull() ?: 0
            ps.puzzleDamageBonus = gi(14); ps.shardHitsBonus = gi(15); ps.clayBonusPct = gi(16)
            ps.sporeBonusPct = gi(17); ps.crystalBonusPct = gi(18); ps.sparkBonusPct = gi(19)
            ps.manaGainPct = gi(20); ps.luckBonus = gi(21); ps.thorns = gi(22)
            ps.armorPerTurn = gi(23); ps.lifestealPct = gi(24); ps.moveCostReduction = gi(25)
            return ps
        }
    }
}

/** Small indirection so combat doesn't hard-depend on the catalogue object graph. */
object CharacterCatalogueById {
    fun get(id: String): CharacterDef =
        com.papergrimoire.characters.CharacterCatalogue.byId(id)
}

/** Tiny enum-map alias kept for readability across combat code. */
typealias EnumMap<K : java.lang.Enum<K>, V> = java.util.EnumMap<K, V>
