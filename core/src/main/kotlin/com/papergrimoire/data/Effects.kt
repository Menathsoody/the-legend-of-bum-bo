package com.papergrimoire.data

import com.papergrimoire.board.PuzzleBoard
import com.papergrimoire.board.TileType
import com.papergrimoire.combat.EnemyInstance
import com.papergrimoire.combat.PlayerState
import com.papergrimoire.combat.StatusKind

/**
 * Central, data-driven effect vocabulary. Every spell, item and artefact is described
 * as a list of these — no per-item classes needed. The EffectEngine interprets them
 * against live game state. This keeps content authoring cheap and saves stable.
 */
sealed class Effect {
    /** Damage one enemy (targeted) or all enemies if target = -1. */
    data class Damage(val amount: Int, val targetIndex: Int = -1) : Effect()
    /** Hit each living enemy n times for [perHit]. Shard-style multi hit. */
    data class MultiHit(val hits: Int, val perHit: Int) : Effect()
    data class GainShield(val amount: Int) : Effect()
    data class Heal(val amount: Int) : Effect()
    data class GainMana(val type: TileType, val amount: Int) : Effect()
    data class GainMoves(val amount: Int) : Effect()
    data class ApplyEnemyStatus(val kind: StatusKind, val value: Int, val turns: Int, val all: Boolean = false) : Effect()
    data class ApplyPlayerStatus(val kind: StatusKind, val value: Int, val turns: Int) : Effect()
    /** Board manipulation: place N curse tiles randomly. */
    data class AddCurses(val count: Int) : Effect()
    data class SealTiles(val count: Int) : Effect()
    data class MarkTiles(val count: Int) : Effect()
    /** Convert N random base tiles to [type] (heart/wild/curse/base). */
    data class ConvertTiles(val type: TileType, val count: Int) : Effect()
    /** Destroy all tiles of a family on the board (no mana gained). */
    data class DestroyFamily(val type: TileType) : Effect()
    data class ShiftRow(val row: Int, val amount: Int) : Effect()
    data class ShiftColumn(val col: Int, val amount: Int) : Effect()
    /** Force-resolve every current match without consuming a move. */
    data object ResolveNow : Effect()
    data class GainCoins(val amount: Int) : Effect()
    data class MaxHpUp(val amount: Int) : Effect()
    data class PuzzlePowerUp(val amount: Int) : Effect()
    data class DrawSpellFromReward(val id: String) : Effect() // used by events/rewards
    /** Artifact-only permanent modifiers, applied when the artefact is picked up. */
    data class PassiveManaGainPct(val pct: Int) : Effect()
    data class PassiveClayBonus(val pct: Int) : Effect()
    data class PassiveCrystalBonus(val pct: Int) : Effect()
    data class PassiveSporeBonus(val pct: Int) : Effect()
    data class PassiveSparkBonus(val pct: Int) : Effect()
    data class PassiveShardHits(val amount: Int) : Effect()
    data class PassiveLuck(val amount: Int) : Effect()
    data class PassiveThorns(val amount: Int) : Effect()
    data class PassiveArmorPerTurn(val amount: Int) : Effect()
    data class PassiveLifesteal(val pct: Int) : Effect()
    data class PassiveExtraMove(val amount: Int) : Effect()
    data class PassiveSpawnWeight(val type: TileType, val delta: Double) : Effect()
}

/** Interpreter for declarative effects. Returns human-readable log lines for the UI. */
object EffectEngine {

    fun apply(
        effects: List<Effect>,
        player: PlayerState,
        board: PuzzleBoard,
        enemies: List<EnemyInstance>,
        rng: com.papergrimoire.util.SeededRng,
        targetedEnemy: Int = -1,
        log: MutableList<String>,
    ) {
        for (e in effects) when (e) {
            is Effect.Damage -> {
                val targets = if (e.targetIndex == -1) enemies.filter { it.alive }
                              else listOfNotNull(enemies.getOrNull(e.targetIndex).takeIf { it?.alive == true })
                for (t in targets) {
                    val dealt = t.takeDamage(e.amount)
                    log += "${t.def.name} takes $dealt"
                    lifesteal(player, dealt, log)
                }
            }
            is Effect.MultiHit -> {
                repeat(e.hits) {
                    val alive = enemies.filter { it.alive }
                    if (alive.isEmpty()) return@repeat
                    val t = if (alive.size == 1) alive[0] else alive[rng.nextInt(alive.size)]
                    val dealt = t.takeDamage(e.perHit)
                    log += "${t.def.name} takes $dealt"
                    lifesteal(player, dealt, log)
                }
            }
            is Effect.GainShield -> { player.shield += e.amount; log += "+${e.amount} shield" }
            is Effect.Heal -> {
                val healed = player.heal(e.amount)
                if (healed > 0) log += "+$healed HP" else log += "healing blocked!"
            }
            is Effect.GainMana -> { player.addMana(e.type, e.amount); log += "+${e.amount} ${e.type.name.lowercase()} mana" }
            is Effect.GainMoves -> { player.movesLeft += e.amount; log += "+${e.amount} moves" }
            is Effect.ApplyEnemyStatus -> {
                val targets = if (e.all) enemies.filter { it.alive }
                              else listOfNotNull(enemies.getOrNull(if (targetedEnemy >= 0) targetedEnemy else 0)?.takeIf { it.alive })
                for (t in targets) t.addStatus(e.kind, 1, e.value, e.turns)
                log += "${e.kind.name.lowercase()} applied"
            }
            is Effect.ApplyPlayerStatus -> { player.addStatus(e.kind, 1, e.value, e.turns) }
            is Effect.AddCurses -> {
                var placed = 0
                outer@ for (i in 0 until e.count * 3) {
                    if (placed >= e.count) break
                    val r = rng.nextInt(board.rows); val c = rng.nextInt(board.cols)
                    val t = board[r, c] ?: continue
                    if (t.type != TileType.CURSE && !t.sealed) {
                        board.setTile(r, c, t.copy(type = TileType.CURSE)); placed++
                    }
                }
                log += "$placed curses seep in"
            }
            is Effect.SealTiles -> { board.sealRandom(e.count); log += "${e.count} tiles sealed" }
            is Effect.MarkTiles -> { board.markRandom(e.count); log += "${e.count} tiles marked" }
            is Effect.ConvertTiles -> {
                var done = 0
                val cells = ArrayList<Pair<Int, Int>>()
                for (r in 0 until board.rows) for (c in 0 until board.cols) cells.add(r to c)
                rng.shuffleInPlace(cells)
                for ((r, c) in cells) {
                    if (done >= e.count) break
                    val t = board[r, c] ?: continue
                    if (t.type == e.type || t.type == TileType.CURSE && e.type == TileType.CURSE) continue
                    board.setTile(r, c, t.copy(type = e.type, sealed = e.type == TileType.SEALED))
                    done++
                }
                log += "${done} tiles become ${e.type.name.lowercase()}"
            }
            is Effect.DestroyFamily -> {
                var n = 0
                for (r in 0 until board.rows) for (c in 0 until board.cols) {
                    if (board[r, c]?.type == e.type) { board.setTile(r, c, null); n++ }
                }
                if (n > 0) { dropAndRefill(board); log += "$n ${e.type.name.lowercase()} destroyed" }
            }
            is Effect.ShiftRow -> board.shiftRow(e.row, e.amount)
            is Effect.ShiftColumn -> board.shiftColumn(e.col, e.amount)
            Effect.ResolveNow -> { /* combat engine triggers resolve directly */ }
            is Effect.GainCoins -> { player.coins += e.amount; log += "+${e.amount} scrip" }
            is Effect.MaxHpUp -> { player.maxHpBonus += e.amount; player.hp += e.amount; log += "+${e.amount} max HP" }
            is Effect.PuzzlePowerUp -> { player.puzzleDamageBonus += e.amount; log += "+${e.amount} puzzle power" }
            is Effect.DrawSpellFromReward -> { if (!player.spells.contains(e.id) && player.spells.size < SPELL_SLOTS) player.spells.add(e.id) }
            is Effect.PassiveManaGainPct -> player.manaGainPct += e.pct
            is Effect.PassiveClayBonus -> player.clayBonusPct += e.pct
            is Effect.PassiveCrystalBonus -> player.crystalBonusPct += e.pct
            is Effect.PassiveSporeBonus -> player.sporeBonusPct += e.pct
            is Effect.PassiveSparkBonus -> player.sparkBonusPct += e.pct
            is Effect.PassiveShardHits -> player.shardHitsBonus += e.amount
            is Effect.PassiveLuck -> player.luckBonus += e.amount
            is Effect.PassiveThorns -> player.thorns += e.amount
            is Effect.PassiveArmorPerTurn -> player.armorPerTurn += e.amount
            is Effect.PassiveLifesteal -> player.lifestealPct += e.pct
            is Effect.PassiveExtraMove -> { player.def.let { } ; player.extraMovesNextTurn += e.amount }
            is Effect.PassiveSpawnWeight -> {
                val w = HashMap(board.currentWeightsForCopy())
                w[e.type] = (w[e.type] ?: 0.0) + e.delta
                board.setSpawnWeights(w)
            }
        }
    }

    private fun lifesteal(player: PlayerState, dealt: Int, log: MutableList<String>) {
        if (player.lifestealPct > 0 && dealt > 0) {
            val heal = (dealt * player.lifestealPct / 100).coerceAtLeast(1)
            val actual = player.heal(heal)
            if (actual > 0) log += "drained $actual HP"
        }
    }

    /** Gravity + refill after external destruction (mirrors board internals). */
    private fun dropAndRefill(board: PuzzleBoard) {
        for (c in 0 until board.cols) {
            var write = board.rows - 1
            for (r in board.rows - 1 downTo 0) {
                val t = board[r, c]
                if (t != null) { if (write != r) { board.setTile(write, c, t); board.setTile(r, c, null) }; write-- }
            }
            for (r in write downTo 0) board.setTile(r, c, board.randomTilePublic())
        }
    }

    const val SPELL_SLOTS = 6
}
