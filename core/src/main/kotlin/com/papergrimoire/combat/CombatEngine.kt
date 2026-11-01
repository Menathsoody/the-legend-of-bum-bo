package com.papergrimoire.combat

import com.papergrimoire.artifacts.ArtifactCatalogue
import com.papergrimoire.board.PuzzleBoard
import com.papergrimoire.board.Tile
import com.papergrimoire.board.TileType
import com.papergrimoire.data.Effect
import com.papergrimoire.data.EffectEngine
import com.papergrimoire.enemies.EnemyAction
import com.papergrimoire.items.ItemCatalogue
import com.papergrimoire.save.Saveable
import com.papergrimoire.spells.SpellCatalogue
import com.papergrimoire.spells.SpellDef
import com.papergrimoire.util.SeededRng

/**
 * The single source of truth for battle logic. Pure Kotlin — no LibGDX imports — so it
 * is unit-testable headless and restorable from saves at any time.
 */
class CombatEngine(
    val player: PlayerState,
    val board: PuzzleBoard,
    val enemies: MutableList<EnemyInstance>,
    val rng: SeededRng,
) {
    enum class Phase { PLAYER_TURN, ENEMY_TURN, WON, LOST }

    var phase: Phase = Phase.PLAYER_TURN
        private set

    /** Accumulated visual/log events since last frame; screens drain this. */
    val combatLog: MutableList<String> = ArrayList()
    val lastResolution: MutableList<PuzzleBoard.ResolutionStep> = ArrayList()

    // per-combat artifact trackers (reset in startCombat)
    private var sparkMatchCount = 0
    private var crystalMatchCount = 0
    private var columnShiftCount = 0
    private var cheatDeathUsed = false
    private var panicUsed = false
    private var firstHeartUsed = false
    private var firstMatchDone = false
    private var itemFreeChargeUsed = false
    private var freeConvertUsed = false
    var targetedEnemyIndex: Int = -1; private set

    private fun hooks(): Set<String> = player.artifacts.flatMap { ArtifactCatalogue.byId(it)?.hooks ?: emptySet() }.toSet()
    private fun hasHook(h: String) = h in hooks()

    // ---------------------------------------------------------------- lifecycle

    fun startCombat() {
        phase = Phase.PLAYER_TURN
        player.shield = 0
        if (hasHook("start_shield")) player.shield += 6
        if (player.armorPerTurn > 0) player.addStatus(StatusKind.ARMOR, 1, player.armorPerTurn, 99)
        if (hasHook("cleanse_start")) {
            player.statuses.removeAll { it.kind == StatusKind.BLEED || it.kind == StatusKind.POISON || it.kind == StatusKind.HEAL_BLOCK }
        }
        beginPlayerTurn(first = true)
    }

    private fun beginPlayerTurn(first: Boolean = false) {
        phase = Phase.PLAYER_TURN
        player.turnCount++
        player.movesLeft = player.def.baseMoves + player.extraMovesNextTurn
        player.extraMovesNextTurn = 0
        if (first && hasHook("first_turn_move")) player.movesLeft += 1
        if (hasHook("every3_move") && player.turnCount % 3 == 0) player.movesLeft += 1
        if (hasHook("lowhp_move") && player.hp <= player.maxHp * 3 / 10) player.movesLeft += 1
        if (hasHook("regen")) player.heal(1)
        if (hasHook("scrip_interest")) { player.coins += 2; player.takeDamage(1) }
        // tick spell cooldowns
        val cdIt = player.cooldowns.entries.iterator()
        while (cdIt.hasNext()) {
            val e = cdIt.next(); e.value -= if (hasHook("cooldown_double") && rng.nextInt(100) < 30) 2 else 1
            if (e.value <= 0) cdIt.remove()
        }
        combatLog += "— turn ${player.turnCount} —"
    }

    // ---------------------------------------------------------------- player actions

    /** Player shifts a row/column: costs one move, then resolves matches & cascades. */
    fun playerShiftRow(row: Int, amount: Int): Boolean {
        if (phase != Phase.PLAYER_TURN || player.movesLeft <= 0) return false
        if (!board.shiftRow(row, amount)) return false
        spendMove(if (hasHook("even_row_free") && row % 2 == 0) 0 else 1)
        afterBoardChange()
        return true
    }

    fun playerShiftColumn(col: Int, amount: Int): Boolean {
        if (phase != Phase.PLAYER_TURN || player.movesLeft <= 0) return false
        if (!board.shiftColumn(col, amount)) return false
        columnShiftCount++
        if (hasHook("shift_mana") && columnShiftCount % 3 == 0) {
            board[0, col]?.let { if (it.type.isBase) player.addMana(it.type, 1) }
        }
        spendMove(1)
        afterBoardChange()
        return true
    }

    private fun spendMove(cost: Int) {
        player.movesLeft = (player.movesLeft - cost).coerceAtLeast(0)
        if (player.movesLeft == 0 && hasHook("second_wind")) {
            // once per turn the permit grants exactly one more move
            player.movesLeft = 1
            combatLog += "Overtime Permit! one more move"
        }
    }

    /** Resolve all matches with cascade loop, applying family effects each step. */
    private fun afterBoardChange() {
        lastResolution.clear()
        var cascade = 0
        while (true) {
            val groups = board.findMatches()
            if (groups.isEmpty()) break
            val step = board.resolveOnce() ?: break
            lastResolution.add(step)
            for (g in step.groups) applyMatchEffect(g.family, g.cells.size, cascade)
            applyResolutionSideEffects(step, cascade)
            cascade++
            if (cascade >= PuzzleBoard.MAX_CASCADES) { combatLog += "the page settles"; break }
        }
        if (hasHook("drop_crystal") && lastResolution.isNotEmpty()) {
            val t = board[board.rows - 1, 4]
            if (t != null) board.setTile(board.rows - 1, 4, t.copy(type = TileType.CRYSTAL))
        }
        if (player.hp <= 0) { phase = Phase.LOST; return }
        if (enemies.none { it.alive }) { winIfOver(); return }
        if (player.movesLeft <= 0) endPlayerTurn(auto = true)
    }

    /** Mana + family effect of one match group. Sizes 4/5/6/7+ have distinct tiers. */
    private fun applyMatchEffect(family: TileType, sizeIn: Int, cascade: Int) {
        var size = sizeIn
        if (hasHook("size_plus_one")) size += 1
        if (hasHook("first_match_up") && !firstMatchDone) { size += 1 }
        firstMatchDone = true
        val power = player.def.puzzlePower + player.puzzleDamageBonus
        val manaGainBase = size / 2 + tierBonus(size)
        val manaPct = player.manaGainPct + if (family == TileType.SPARK && hasHook("wool_cap")) 0 else 0
        var mana = manaGainBase * (100 + manaPct) / 100
        if (family == TileType.SPARK) mana = mana * (100 + player.sparkBonusPct) / 100
        player.addMana(family, mana)
        combatLog += "$family x$size -> +$mana mana"

        when (family) {
            TileType.SHARD -> {
                val hits = (size - 2) + player.def.attackPower / 2 + player.shardHitsBonus
                val perHit = (2 + size / 2 + power + flatFor(family)).coerceAtLeast(1)
                EffectEngine.apply(listOf(Effect.MultiHit(hits.coerceAtLeast(1), perHit)), player, board, enemies, rng, log = combatLog)
            }
            TileType.CLAY -> {
                var shield = (3 + size + power + flatFor(family)) * (100 + player.clayBonusPct) / 100
                if (hasHook("shield_flat")) shield += 3
                player.shield += shield
                combatLog += "+$shield shield"
                if (hasHook("clay_coin")) player.coins += 1
                if (hasHook("clay_heal")) player.heal(2)
            }
            TileType.SPORE -> {
                val dur = ((size / 4) + 1) * (100 + player.sporeBonusPct) / 100
                val targets = enemies.filter { it.alive }
                if (targets.isNotEmpty()) {
                    val stunRoll = rng.nextInt(100)
                    val target = targets.minByOrNull { it.hp }!!
                    if (stunRoll < 35 + size * 5) {
                        target.addStatus(StatusKind.STUN, 1, 0, dur.coerceAtLeast(1))
                        combatLog += "${target.def.name} stunned!"
                    } else target.addStatus(StatusKind.WEAKENED, 1, 20, dur.coerceAtLeast(1))
                }
                if (hasHook("spore_poison_all")) enemies.filter { it.alive }.forEach { it.addStatus(StatusKind.POISON, 1, 1, 2) }
                if (hasHook("spore_heal")) player.heal(size)
            }
            TileType.CRYSTAL -> {
                crystalMatchCount++
                var dmg = (4 + size * 2 + power + flatFor(family)) * (100 + player.crystalBonusPct) / 100
                if (hasHook("mega_triple") && size >= 7) dmg *= 3
                if (hasHook("crystal_every3") && crystalMatchCount % 3 == 0) dmg = dmg * 2
                val t = pickTarget()
                if (t != null) {
                    val dealt = t.takeDamage(dmg)
                    combatLog += "${t.def.name} takes $dealt crystal"
                    if (player.lifestealPct > 0) player.heal(dealt * player.lifestealPct / 100)
                }
            }
            TileType.SPARK -> {
                sparkMatchCount++
                if (size >= 5) { player.movesLeft += 1; combatLog += "+1 move" }
                if (hasHook("spark_move_every4") && sparkMatchCount % 4 == 0) { player.movesLeft += 1; combatLog += "+1 move (nib)" }
            }
            TileType.HEART -> {}
            TileType.CURSE -> {}
            TileType.WILD -> {}
            TileType.SEALED -> {}
        }
        if (hasHook("cross_damage") && lastResolution.sumOf { it.groups.size } > 1) {
            pickTarget()?.let { it.takeDamage(4); combatLog += "cross bonus 4" }
        }
        if (hasHook("cross_move") && lastResolution.lastOrNull()?.groups?.size?.let { it > 1 } == true) {
            player.movesLeft += 1; combatLog += "cross-stitch +1 move"
        }
        if (hasHook("long_match_move") && size >= 6) { player.movesLeft += 1; combatLog += "+1 move (domino)" }
        if (hasHook("lowhp_damage") && player.hp <= player.maxHp * 3 / 10) pickTarget()?.takeDamage(4)
        if (hasHook("cascade_power") && cascade > 0) pickTarget()?.takeDamage(2)
        if (hasHook("match_minus")) pickTarget()?.let { /* nerf trade already paid via passive */ }
    }

    private fun flatFor(family: TileType): Int {
        var f = 0
        val arts = player.artifacts.mapNotNull { ArtifactCatalogue.byId(it) }
        if (family == TileType.SHARD && arts.any { "shard_flat" in it.hooks }) f += 2
        if (family in listOf(TileType.SHARD, TileType.CRYSTAL) && arts.any { "shard_crystal_flat" in it.hooks }) f += 2
        if (arts.any { "splinter_crown" == it.id }) if (family == TileType.SHARD) f += 4
        return f
    }

    private fun tierBonus(size: Int): Int = when {
        size >= 7 -> 4
        size >= 6 -> 3
        size >= 5 -> 2
        else -> 1
    }

    private fun applyResolutionSideEffects(step: PuzzleBoard.ResolutionStep, cascade: Int) {
        if (step.heartsHealed > 0) {
            var heal = step.heartsHealed * (6 + player.luckBonus + player.def.luck / 3)
            if (hasHook("heart_boost")) heal += 2 * step.heartsHealed
            if (hasHook("first_heart_double") && !firstHeartUsed) { heal *= 2; firstHeartUsed = true }
            player.heal(heal)
            combatLog += "hearts restore $heal HP"
        }
        if (step.curseDestroyed.isNotEmpty()) {
            val n = step.curseDestroyed.size
            if (hasHook("curse_heal")) player.heal(3 * n)
            if (hasHook("curse_coin")) player.coins += n
            combatLog += "$n curses unraveled"
        }
        if (hasHook("quiet_confetti") && cascade >= 1 && rng.nextInt(100) < 15 + player.luckBonus + player.def.luck) {
            // sprinkle a heart somewhere
            val r = rng.nextInt(board.rows); val c = rng.nextInt(board.cols)
            board[r, c]?.let { board.setTile(r, c, it.copy(type = TileType.HEART)) }
        }
    }

    private fun pickTarget(): EnemyInstance? {
        val alive = enemies.filter { it.alive }
        if (alive.isEmpty()) return null
        if (targetedEnemyIndex in alive.indices) return alive[targetedEnemyIndex]
        return alive.first()
    }

    fun selectTarget(index: Int) {
        val alive = enemies.filterIndexed { i, e -> e.alive }
        if (index in alive.indices) {
            targetedEnemyIndex = enemies.indexOf(alive[index])
        }
    }

    /** End the player's turn explicitly (button) or automatically (moves exhausted). */
    fun endPlayerTurn(auto: Boolean = false) {
        if (phase != Phase.PLAYER_TURN) return
        // marked tiles pop at end of turn
        val popped = board.popMarkedTiles()
        if (popped.isNotEmpty()) combatLog += "${popped.size} marked tiles burst"
        if (popped.isNotEmpty()) afterBoardChangeQuiet()
        phase = Phase.ENEMY_TURN
        runEnemyTurn()
    }

    /** resolve cascades without consuming moves or ending turn (post-pop drops). */
    private fun afterBoardChangeQuiet() {
        var guard = 0
        while (guard++ < PuzzleBoard.MAX_CASCADES) {
            val step = board.resolveOnce() ?: break
            for (g in step.groups) applyMatchEffect(g.family, g.cells.size, guard)
        }
    }

    // ---------------------------------------------------------------- spells & items

    fun canCast(spellId: String): Boolean {
        val def = SpellCatalogue.byId(spellId) ?: return false
        if (!player.spells.contains(spellId)) return false
        if (player.cooldowns.containsKey(spellId)) return false
        return affordableCost(def.cost)
    }

    private fun affordableCost(cost: Map<TileType, Int>): Boolean {
        val discount = if (hasHook("spell_cost_less")) 1 else 0
        for ((t, v) in cost) {
            val need = (v - discount).coerceAtLeast(t.let { if (discount > 0) 0 else v })
            if (need > 0 && player.mana(t) < need) return false
        }
        return true
    }

    fun castSpell(spellId: String, target: Int = -1): Boolean {
        val def = SpellCatalogue.byId(spellId) ?: return false
        if (!canCast(spellId)) return false
        if (def.needsTarget && target < 0) return false
        val discount = if (hasHook("spell_cost_less")) 1 else 0
        for ((t, v) in def.cost) {
            val need = if (discount > 0) (v - 1).coerceAtLeast(0) else v
            if (need > 0) { if (!player.spendMana(t, need)) return false }
        }
        if (target >= 0) targetedEnemyIndex = target
        val effects = adjustSpellEffects(def)
        EffectEngine.apply(effects, player, board, enemies, rng, targetedEnemy = targetedEnemyIndex, log = combatLog)
        if (def.effects.any { it == Effect.ResolveNow }) forceResolve()
        if (def.cooldown > 0) {
            val cd = if (hasHook("cooldown_less")) (def.cooldown - 1).coerceAtLeast(1) else def.cooldown
            player.cooldowns[spellId] = cd
        }
        if (hasHook("spell_double") && rng.nextInt(100) < 10) {
            combatLog += "double signature!"
            EffectEngine.apply(effects, player, board, enemies, rng, targetedEnemy = targetedEnemyIndex, log = combatLog)
        }
        if (hasHook("utility_spark") && def.school == SpellDef.School.UTILITY) player.addMana(TileType.SPARK, 1)
        combatLog += "cast ${def.name}"
        checkOutcome()
        return true
    }

    private fun adjustSpellEffects(def: SpellDef): List<Effect> {
        var list = def.effects
        if (hasHook("spell_attack_up") && def.school == SpellDef.School.ATTACK) {
            list = list.map { if (it is Effect.Damage) it.copy(amount = it.amount + 3) else it }
        }
        if (hasHook("control_longer") && def.school == SpellDef.School.CONTROL) {
            list = list.map { if (it is Effect.ApplyEnemyStatus) it.copy(turns = it.turns + 1) else it }
        }
        return list
    }

    private fun forceResolve() {
        var guard = 0
        while (guard++ < PuzzleBoard.MAX_CASCADES) {
            val groups = board.findMatches()
            if (groups.isEmpty()) break
            val step = board.resolveOnce() ?: break
            lastResolution.add(step)
            for (g in step.groups) applyMatchEffect(g.family, g.cells.size, guard)
            applyResolutionSideEffects(step, guard)
        }
    }

    fun useItem(itemId: String, target: Int = -1): Boolean {
        val charges = player.items[itemId] ?: return false
        val def = ItemCatalogue.byId(itemId) ?: return false
        if (def.needsTarget && target < 0) return false
        if (target >= 0) targetedEnemyIndex = target
        val free = hasHook("item_free_charge") && !itemFreeChargeUsed
        if (!free) player.items[itemId] = charges - 1
        if (player.items[itemId]!! <= 0) player.items.remove(itemId)
        if (free) itemFreeChargeUsed = true
        EffectEngine.apply(def.effects, player, board, enemies, rng, targetedEnemy = targetedEnemyIndex, log = combatLog)
        if (def.effects.any { it == Effect.ResolveNow }) forceResolve()
        combatLog += "used ${def.name}"
        checkOutcome()
        return true
    }

    /** Heretic's Marker: tap a tile to convert it to the least-represented family. */
    fun freeConvertTile(r: Int, c: Int): Boolean {
        if (!hasHook("free_convert") || freeConvertUsed) return false
        val t = board[r, c] ?: return false
        if (!t.type.isBase) return false
        val counts = TileType.BASE.associateWith { board.countType(it) }
        val min = counts.minByOrNull { it.value }!!.key
        board.setTile(r, c, t.copy(type = min))
        freeConvertUsed = true
        combatLog += "marker rewrites a tile"
        afterBoardChange()
        return true
    }

    // ---------------------------------------------------------------- enemy turn

    private fun runEnemyTurn() {
        for (e in enemies.filter { it.alive }.toList()) {
            if (hasHook("enemy_forget") && rng.nextInt(100) < 15) { combatLog += "${e.def.name} forgets you"; continue }
            val action = e.chooseAction(rng)
            executeEnemyAction(e, action)
            if (player.hp <= 0) break
        }
        if (player.hp <= 0) { phase = Phase.LOST; return }
        // doom ticks etc handled inside statuses next chooseAction
        beginPlayerTurn()
    }

    private fun executeEnemyAction(e: EnemyInstance, action: EnemyAction) {
        when (action) {
            is EnemyAction.Attack -> dealToPlayer(e, action.damage, action.statusKind, action.statusValue)
            is EnemyAction.HeavyAttack -> {
                // telegraph this turn, land next turn
                if (e.telegraphedHeavy == 0) {
                    e.telegraphedHeavy = action.damage
                    combatLog += "${e.def.name} WINDS UP (${action.damage})!"
                } else {
                    val dmg = e.telegraphedHeavy
                    e.telegraphedHeavy = 0
                    dealToPlayer(e, dmg, null, 0)
                }
            }
            EnemyAction.Defend -> { e.shield += e.def.hp / 8; combatLog += "${e.def.name} braces" }
            is EnemyAction.GainShield -> { e.shield += action.amount }
            is EnemyAction.ApplyToPlayer -> {
                if (action.kind == StatusKind.POISON && hasHook("immune_poison")) return
                if (action.kind == StatusKind.BLEED && hasHook("immune_bleed")) return
                player.addStatus(action.kind, 1, action.value, action.turns)
                combatLog += "${e.def.name}: ${action.kind.name.lowercase()} on you"
            }
            is EnemyAction.BoardCurse -> EffectEngine.apply(listOf(Effect.AddCurses(action.count)), player, board, enemies, rng, log = combatLog)
            EnemyAction.BoardSeal -> EffectEngine.apply(listOf(Effect.SealTiles(2)), player, board, enemies, rng, log = combatLog)
            EnemyAction.BoardMark -> EffectEngine.apply(listOf(Effect.MarkTiles(3)), player, board, enemies, rng, log = combatLog)
            is EnemyAction.ShiftBoardRow -> { board.shiftRow(action.row, action.amount); combatLog += "${e.def.name} warps row ${action.row + 1}" }
            is EnemyAction.Summon -> {
                if (enemies.count { it.alive } < MAX_ENEMIES) {
                    val def = com.papergrimoire.enemies.EnemyCatalogue.byId(action.enemyId)
                    if (def != null) {
                        enemies.add(EnemyInstance(def))
                        combatLog += "${e.def.name} summons ${def.name}!"
                    }
                }
            }
            is EnemyAction.BuffAlly -> {
                enemies.filter { it.alive && it !== e }.forEach { it.buffPercent += action.percent }
                combatLog += "${e.def.name} inspires allies (+${action.percent}%)"
            }
            is EnemyAction.HealSelf -> { e.heal(action.amount); combatLog += "${e.def.name} heals ${action.amount}" }
            is EnemyAction.Steal -> {
                val stolen = minOf(player.coins, action.coins)
                player.coins -= stolen
                combatLog += "${e.def.name} steals $stolen scrip"
            }
            is EnemyAction.PoisonCloud -> {
                if (!hasHook("immune_poison")) player.addStatus(StatusKind.POISON, action.stacks, 2, 3)
            }
            EnemyAction.SleepMist -> player.addStatus(StatusKind.HEAL_BLOCK, 1, 0, 2)
            is EnemyAction.ConvertTiles -> EffectEngine.apply(listOf(Effect.ConvertTiles(action.to, action.count)), player, board, enemies, rng, log = combatLog)
            EnemyAction.DoNothing -> {}
        }
        e.updatePhase()?.let { combatLog += "${e.def.name} enters a NEW PHASE!" }
    }

    private fun dealToPlayer(e: EnemyInstance, baseDamage: Int, statusKind: StatusKind?, statusValue: Int) {
        var dmg = baseDamage
        dmg = dmg * (100 + e.buffPercent) / 100
        dmg = dmg * (100 - e.statusValue(StatusKind.WEAKENED)) / 100
        if (hasHook("dodge") && rng.nextInt(100) < 10) { combatLog += "you dodge!"; return }
        val before = player.hp + player.shield
        val hpLost = player.takeDamage(dmg)
        if (statusKind != null) {
            if (statusKind == StatusKind.BLEED && hasHook("immune_bleed")) { }
            else if (statusKind == StatusKind.POISON && hasHook("immune_poison")) { }
            else player.addStatus(statusKind, 1, statusValue, 3)
        }
        combatLog += "${e.def.name} hits you for ${before - (player.hp + player.shield)}"
        // thorns
        val thorns = player.thorns + player.statusValue(StatusKind.THORNS)
        if (thorns > 0 && hpLost > 0) { e.takeDamage(thorns); combatLog += "thorns bite back $thorns" }
        if (hasHook("panic_shield") && !panicUsed && player.hp <= player.maxHp * 2 / 10) {
            player.shield += 12; panicUsed = true; combatLog += "PANIC BUTTON: +12 shield"
        }
        if (player.hp <= 0) {
            if (hasHook("cheat_death") && !cheatDeathUsed) {
                player.hp = 1; cheatDeathUsed = true; combatLog += "THE LAST PAGE turns death down!"
            }
        }
    }

    // ---------------------------------------------------------------- outcome

    private fun checkOutcome() {
        if (player.hp <= 0) { phase = Phase.LOST; return }
        if (enemies.none { it.alive }) winIfOver()
    }

    private fun winIfOver() {
        phase = Phase.WON
    }

    val victoryGold: Int
        get() = enemies.sumOf { e ->
            val base = e.def.rollGold(rng)
            var g = base
            if (hasHook("coin_up")) g = g * 5 / 4
            if (hasHook("victory_coin")) g += 4
            if (e.def.isBoss && hasHook("boss_coin")) g += 15
            g
        }

    companion object { const val MAX_ENEMIES = 5 }

    // ---------------------------------------------------------------- save/load

    fun saveState(): CombatSave = CombatSave(
        phase.name, player.saveToString(), board.serialize(),
        enemies.joinToString("~") { it.saveToString() },
        listOf(sparkMatchCount, crystalMatchCount, columnShiftCount,
            if (cheatDeathUsed) 1 else 0, if (panicUsed) 1 else 0,
            if (firstHeartUsed) 1 else 0, if (firstMatchDone) 1 else 0,
            if (itemFreeChargeUsed) 1 else 0, if (freeConvertUsed) 1 else 0,
            targetedEnemyIndex).joinToString(","),
    )

    data class CombatSave(val phase: String, val player: String, val board: String, val enemies: String, val counters: String)

    companionobjectLoader@ SuppressWarnings("")
}

/** Rehydrates a CombatEngine from saved strings. Returns null on corrupt data. */
fun loadCombat(save: CombatEngine.CombatSave, rng: SeededRng): CombatEngine? {
    return try {
        val player = PlayerState.loadFromString(save.player)
        val board = PuzzleBoard.deserialize(save.board, rng)
        val enemies = if (save.enemies.isBlank()) mutableListOf()
                      else save.enemies.split("~").mapNotNull { EnemyInstance.loadFromString(it) }.toMutableList()
        val engine = CombatEngine(player, board, enemies, rng)
        engine.phaseRestore(save.phase)
        val cf = save.counters.split(",").map { it.toInt() }
        engine.restoreCounters(cf)
        engine
    } catch (e: Exception) {
        null
    }
}
