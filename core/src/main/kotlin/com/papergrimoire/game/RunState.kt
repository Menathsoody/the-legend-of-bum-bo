package com.papergrimoire.game

import com.papergrimoire.artifacts.ArtifactCatalogue
import com.papergrimoire.board.PuzzleBoard
import com.papergrimoire.characters.CharacterCatalogue
import com.papergrimoire.combat.CombatEngine
import com.papergrimoire.combat.EnemyInstance
import com.papergrimoire.combat.PlayerState
import com.papergrimoire.combat.loadCombat
import com.papergrimoire.data.Effect
import com.papergrimoire.data.EffectEngine
import com.papergrimoire.dungeon.AreaCatalogue
import com.papergrimoire.dungeon.DungeonGenerator
import com.papergrimoire.dungeon.FloorMap
import com.papergrimoire.dungeon.RoomNode
import com.papergrimoire.dungeon.RoomType
import com.papergrimoire.items.ItemCatalogue
import com.papergrimoire.items.Rarity
import com.papergrimoire.spells.SpellCatalogue
import com.papergrimoire.util.SeededRng

/** Difficulty presets: scale enemy strength and reward flow. */
enum class Difficulty(val id: String, val label: String, val enemyHpPct: Int, val goldPct: Int) {
    NOVICE("novice", "Novice Scribe", 85, 120),
    STANDARD("standard", "Journeyman", 100, 100),
    HARSH("harsh", "Harsh Margin", 120, 90),
    BRUTAL("brutal", "Red-Pen Brutality", 145, 80);

    companion object {
        fun byId(id: String): Difficulty = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}

/** What the player is currently doing inside a run. */
enum class RunStage { MAP, COMBAT, REWARD, SHOP, REST, EVENT, GAME_OVER, VICTORY }

/** Per-run counters shown on the summary screen. */
class RunStats {
    var combatsWon = 0
    var turnsPlayed = 0
    var goldEarned = 0
    var scripSpent = 0
    var itemsFound = 0
    var artifactsFound = 0
    var spellsLearned = 0
    var runsWon = 0
    var deepestFloor = 1

    fun serialize(): String = listOf(combatsWon, turnsPlayed, goldEarned, scripSpent, itemsFound, artifactsFound, spellsLearned).joinToString(",")
    fun restore(data: String) {
        val f = data.split(",").mapNotNull { it.toIntOrNull() }
        combatsWon = f.getOrElse(0) { 0 }; turnsPlayed = f.getOrElse(1) { 0 }; goldEarned = f.getOrElse(2) { 0 }
        scripSpent = f.getOrElse(3) { 0 }; itemsFound = f.getOrElse(4) { 0 }; artifactsFound = f.getOrElse(5) { 0 }
        spellsLearned = f.getOrElse(6) { 0 }
    }
}

/**
 * The whole run: seed-derived RNGs, floor maps, player state and the active combat.
 * Everything serialises to one string bundle for mid-run saves (see SaveManager).
 */
class RunState(
    val seed: Long,
    val characterId: String,
    val difficulty: Difficulty,
    prebuiltMap: FloorMap? = null,
    prebuiltPlayer: PlayerState? = null,
) {
    val masterRng = SeededRng(seed xor 0x5EED1A7EL)
    var floorNumber: Int = 1
    var map: FloorMap = prebuiltMap ?: DungeonGenerator.generateFloor(floorNumber, SeededRng(dungeonSeed(floorNumber)))
    var currentRoomId: Int = -1
    var stage: RunStage = RunStage.MAP
    val player: PlayerState = prebuiltPlayer ?: PlayerState(CharacterCatalogue.byId(characterId))
    var combat: CombatEngine? = null
    /** shop stock / reward offers as "kind:id" strings */
    var shopStock: MutableList<String> = mutableListOf()
    var shopPrices: MutableList<Int> = mutableListOf()
    var rewardOffers: MutableList<String> = mutableListOf()
    var eventPrompt: String = ""
    var eventChoices: MutableList<String> = mutableListOf()
    var eventOutcome: String = ""
    var eventId: String = ""
    var restUsed: Boolean = false
    var stats = RunStats()
    var ended: Boolean = false
    var won: Boolean = false

    init {
        player.spells.addAll(player.def.startingSpells)
        for (iid in player.def.startingItems) player.items[iid] = ItemCatalogue.byId(iid)?.charges ?: 1
        player.coins = 15
        applyCharacterPassive()
    }

    private fun applyCharacterPassive() {
        when (player.def.passiveId) {
            "shard_blood" -> player.shardHitsBonus += 1
            "wet_clay" -> player.clayBonusPct += 25
            "spore_network" -> player.sporeBonusPct += 30
            "overclock" -> Unit // extra moves come from match play; tinker already has 4 base
            "deep_resonance" -> player.crystalBonusPct += 25
            "margin_magic" -> player.luckBonus += 3
        }
    }

    fun dungeonSeed(floor: Int): Long = seed xor (floor.toLong() * -7046029254386353131L)

    // ------------------------------------------------------------ map navigation

    fun canEnter(room: RoomNode): Boolean =
        room.col == 0 || currentRoomId < 0 || map.outgoing(currentRoomId).any { it.id == room.id }

    enum class EnterKind { STARTED_COMBAT, GOT_REWARD, ENTERED_SHOP, ENTERED_REST, ENTERED_EVENT }

    fun enterRoom(room: RoomNode): EnterKind {
        require(canEnter(room)) { "room not reachable" }
        currentRoomId = room.id
        room.visited = true
        restUsed = false
        return when (room.type) {
            RoomType.COMBAT, RoomType.ELITE, RoomType.BOSS -> {
                startCombatInRoom(room)
                EnterKind.STARTED_COMBAT
            }
            RoomType.TREASURE -> { openTreasure(room); EnterKind.GOT_REWARD }
            RoomType.SHOP -> { generateShop(); stage = RunStage.SHOP; EnterKind.ENTERED_SHOP }
            RoomType.REST -> { stage = RunStage.REST; EnterKind.ENTERED_REST }
            RoomType.EVENT -> { generateEvent(); stage = RunStage.EVENT; EnterKind.ENTERED_EVENT }
        }
    }

    private fun rngForRoom(): SeededRng =
        SeededRng((seed xor (floorNumber * 131L) xor (currentRoomId * 7919L)) and Long.MAX_VALUE)

    fun startCombatInRoom(room: RoomNode) {
        val r = rngForRoom()
        val diffIdx = if (difficulty == Difficulty.HARSH || difficulty == Difficulty.BRUTAL) 1 else 0
        val ids = DungeonGenerator.enemiesFor(map, room, difficulty = diffIdx, rng = r)
        val enemies = ids.mapNotNull { com.papergrimoire.enemies.EnemyCatalogue.byId(it) }.map { def ->
            val e = EnemyInstance(def)
            e.hp = (e.hp * difficulty.enemyHpPct / 100).coerceAtLeast(1)
            e
        }.toMutableList()
        if (enemies.isEmpty()) return
        val board = PuzzleBoard(rng = SeededRng(r.nextLong()))
        combat = CombatEngine(player, board, enemies, SeededRng(r.nextLong()))
        combat?.startCombat()
        stage = RunStage.COMBAT
        if (floorNumber > stats.deepestFloor) stats.deepestFloor = floorNumber
    }

    /** Called by the screen after combat ends; grants rewards & advances. */
    fun finishCombat(victory: Boolean) {
        val c = combat ?: return
        if (!victory) { stage = RunStage.GAME_OVER; ended = true; return }
        stats.combatsWon++
        stats.turnsPlayed += player.turnCount
        val goldBase = c.victoryGold * difficulty.goldPct / 100
        player.coins += goldBase
        stats.goldEarned += goldBase
        map.room(currentRoomId)?.cleared = true
        buildRewardOffers(c.enemies.any { it.def.isBoss })
        stage = RunStage.REWARD
    }

    fun leaveReward() {
        rewardOffers.clear()
        advanceToNextFloorOrVictory()
    }

    private fun advanceToNextFloorOrVictory() {
        val cur = map.room(currentRoomId)
        val atLastColumn = cur != null && cur.col == map.cols - 1
        if (!atLastColumn) { stage = RunStage.MAP; return }
        if (floorNumber >= AreaCatalogue.MAX_FLOOR) {
            stage = RunStage.VICTORY; ended = true; won = true; return
        }
        floorNumber++
        map = DungeonGenerator.generateFloor(floorNumber, SeededRng(dungeonSeed(floorNumber)))
        currentRoomId = -1
        stage = RunStage.MAP
        combat = null
    }

    /** Leaving a utility room: advance floors if we were at the last column. */
    fun maybeAdvanceFloor() {
        val cur = map.room(currentRoomId) ?: run { stage = RunStage.MAP; return }
        if (cur.col == map.cols - 1) advanceToNextFloorOrVictory() else stage = RunStage.MAP
    }

    // ------------------------------------------------------------ rewards / shop / treasure

    private fun buildRewardOffers(bossKill: Boolean) {
        rewardOffers.clear()
        val r = SeededRng(seed xor floorNumber * 977L xor player.coins.toLong())
        repeat(if (bossKill) 3 else 2) { rewardOffers.add(rollOffer(r, bossRoll = bossKill)) }
    }

    private fun rollOffer(rng: SeededRng, bossRoll: Boolean): String {
        val kind = rng.weighted(listOf("item" to 40.0, "artifact" to 35.0, "spell" to 25.0))
        return when (kind) {
            "item" -> "i:" + pickItem(rng, bossRoll).id
            "artifact" -> "a:" + pickArtifact(rng, bossRoll).id
            else -> "s:" + pickSpell(rng).id
        }
    }

    private fun rarityWeights(bossRoll: Boolean): List<Pair<Rarity, Double>> = when {
        bossRoll -> listOf(Rarity.COMMON to 10.0, Rarity.UNCOMMON to 25.0, Rarity.RARE to 30.0, Rarity.EPIC to 25.0, Rarity.LEGENDARY to 10.0)
        else -> Rarity.entries.map { it to it.weight }
    }

    internal fun pickItem(rng: SeededRng, bossRoll: Boolean) =
        filterByRarity(ItemCatalogue.all.map { it.rarity to it }, rng, bossRoll)

    internal fun pickArtifact(rng: SeededRng, bossRoll: Boolean) =
        filterByRarity(ArtifactCatalogue.all.map { it.rarity to it }, rng, bossRoll)

    internal fun pickSpell(rng: SeededRng) =
        SpellCatalogue.all.filter { !player.spells.contains(it.id) }
            .let { if (it.isEmpty()) SpellCatalogue.all else it }
            .let { rng.pick(it) }

    private fun <T : Any> filterByRarity(pairs: List<Pair<Rarity, T>>, rng: SeededRng, bossRoll: Boolean): T {
        val rar = rng.weighted(rarityWeights(bossRoll))
        val pool = pairs.filter { it.first == rar }.map { it.second }
        return if (pool.isNotEmpty()) rng.pick(pool) else rng.pick(pairs.map { it.second })
    }

    fun takeReward(index: Int) {
        if (index !in rewardOffers.indices) return
        applyOffer(rewardOffers[index])
        rewardOffers.removeAt(index)
        if (rewardOffers.isEmpty()) leaveReward()
    }

    fun skipRewards() { leaveReward() }

    private fun applyOffer(offer: String) {
        val parts = offer.split(":")
        if (parts.size < 2) return
        val kind = parts[0]; val id = parts[1]
        when (kind) {
            "i" -> {
                val def = ItemCatalogue.byId(id) ?: return
                player.items[id] = (player.items[id] ?: 0) + def.charges
                stats.itemsFound++
            }
            "a" -> {
                val def = ArtifactCatalogue.byId(id) ?: return
                if (!player.artifacts.contains(id)) player.artifacts.add(id)
                val dummyLog = ArrayList<String>()
                EffectEngine.apply(def.effects, player,
                    combat?.board ?: PuzzleBoard(rng = SeededRng(1)),
                    combat?.enemies ?: mutableListOf(), masterRng, log = dummyLog)
                stats.artifactsFound++
            }
            "s" -> {
                if (!player.spells.contains(id)) {
                    if (player.spells.size >= EffectEngine.SPELL_SLOTS) player.spells.removeAt(0)
                    player.spells.add(id)
                }
                stats.spellsLearned++
            }
        }
    }

    private fun openTreasure(room: RoomNode) {
        room.cleared = true
        val r = SeededRng(seed xor room.id * 313L)
        player.coins += r.nextInt(8, 20) * difficulty.goldPct / 100
        buildRewardOffers(bossKill = false)
        if (rewardOffers.size > 1) rewardOffers = rewardOffers.take(1).toMutableList()
        stage = RunStage.REWARD
    }

    fun generateShop() {
        shopStock.clear(); shopPrices.clear()
        val r = SeededRng(seed xor floorNumber * 71L xor 0x5A0CL)
        val scale = 10 * floorNumber
        repeat(5) {
            val roll = r.nextInt(100)
            when {
                roll < 40 -> { val o = pickItem(r, false); shopStock.add("i:" + o.id); shopPrices.add((o.price + scale) * 100 / difficulty.goldPct) }
                roll < 75 -> { val a = pickArtifact(r, false); shopStock.add("a:" + a.id); shopPrices.add(priceFor(a.rarity) + scale) }
                else -> { val s = pickSpell(r); shopStock.add("s:" + s.id); shopPrices.add(spellPrice(s) + scale) }
            }
        }
        shopStock.add("h:heal"); shopPrices.add(8 + scale / 2)
    }

    private fun priceFor(rr: Rarity) = when (rr) {
        Rarity.COMMON -> 15; Rarity.UNCOMMON -> 22; Rarity.RARE -> 32; Rarity.EPIC -> 45; Rarity.LEGENDARY -> 65
    }
    private fun spellPrice(s: com.papergrimoire.spells.SpellDef) = 14 + s.cost.values.sum() * 3

    fun buyShopItem(index: Int): Boolean {
        val offer = shopStock.getOrNull(index) ?: return false
        val price = shopPrices.getOrElse(index) { Int.MAX_VALUE }
        if (player.coins < price) return false
        if (offer == "h:heal") {
            player.heal(20)
            player.statuses.removeAll { it.kind == com.papergrimoire.combat.StatusKind.POISON || it.kind == com.papergrimoire.combat.StatusKind.BLEED }
        } else applyOffer(offer)
        player.coins -= price
        shopStock.removeAt(index); shopPrices.removeAt(index)
        stats.scripSpent += price
        return true
    }

    fun leaveShop() { maybeAdvanceFloor() }

    fun useRest(kind: String) {
        if (restUsed) return
        restUsed = true
        when (kind) {
            "heal" -> player.heal(player.maxHp * 30 / 100)
            "cleanse" -> player.statuses.clear()
            "upgrade" -> for (k in player.items.keys.toList()) {
                val def = ItemCatalogue.byId(k) ?: continue
                player.items[k] = (player.items[k] ?: 0) + minOf(1, def.charges)
            }
            "study" -> player.puzzleDamageBonus += 1
        }
        maybeAdvanceFloor()
    }

    // ------------------------------------------------------------ events

    class EventDef(
        val id: String,
        val prompt: String,
        val choices: List<String>,
        val outcomes: List<(RunState, SeededRng) -> String>,
    )

    private val events: List<EventDef> = listOf(
        EventDef("well", "A wishing well gurgles. Scrip floats on top.",
            listOf("Toss 10 scrip", "Drink from it", "Leave"),
            listOf(
                { rs, r ->
                    if (rs.player.coins >= 10) {
                        rs.player.coins -= 10
                        if (r.nextInt(100) < 40) {
                            val a = rs.pickArtifact(r, false); rs.player.artifacts.add(a.id)
                            "The well coughs up a ${a.name}!"
                        } else { rs.player.coins += 25; "You fish out 25 wet scrip." }
                    } else "Your purse is too light."
                },
                { rs, _ ->
                    rs.player.addStatus(com.papergrimoire.combat.StatusKind.POISON, 1, 2, 2)
                    rs.player.heal(15); "Warm, sweet... mostly fine. +15 HP, slight poison."
                },
                { _, _ -> "You walk on. The well sighs." },
            )),
        EventDef("beggar", "A beggar made of folded receipts holds out a hat.",
            listOf("Give 5 scrip", "Rob the beggar", "Walk past"),
            listOf(
                { rs, r ->
                    if (rs.player.coins >= 5) rs.player.coins -= 5
                    if (r.nextInt(100) < 60) {
                        val a = rs.pickArtifact(r, false); rs.player.artifacts.add(a.id)
                        "The beggar blesses you with a ${a.name}."
                    } else "He just says thank you."
                },
                { rs, _ ->
                    rs.player.coins += 12
                    rs.curseBoard(2)
                    "You shake 12 scrip loose. Something sticky follows you."
                },
                { _, _ -> "He watches you go." },
            )),
        EventDef("shrine", "A shrine to Saint Errata, patron of corrected mistakes.",
            listOf("Pray (+8 max HP)", "Desecrate (gain 30 scrip)", "Leave"),
            listOf(
                { rs, _ -> rs.player.maxHpBonus += 8; rs.player.hp += 8; "Saint Errata edits your margins wider." },
                { rs, _ ->
                    rs.player.coins += 30
                    rs.player.addStatus(com.papergrimoire.combat.StatusKind.BLEED, 1, 1, 999)
                    "The offering bowl empties into your pockets. Bleed 1 lingers forever."
                },
                { _, _ -> "The shrine is indifferent." },
            )),
    )

    private fun curseBoard(count: Int) {
        val c = combat ?: return
        EffectEngine.apply(listOf(Effect.AddCurses(count)), c.player, c.board, c.enemies, c.rng, log = c.combatLog)
    }

    fun generateEvent() {
        val r = SeededRng((seed xor (currentRoomId.toLong() * 1009L) xor floorNumber.toLong()) and Long.MAX_VALUE)
        val ev = r.pick(events)
        eventId = ev.id
        eventPrompt = ev.prompt
        eventChoices = ev.choices.toMutableList()
        eventOutcome = ""
    }

    fun chooseEvent(index: Int) {
        val ev = events.firstOrNull { it.id == eventId } ?: return
        if (index !in ev.outcomes.indices) return
        val r = SeededRng((seed xor currentRoomId * 7727L) and Long.MAX_VALUE)
        eventOutcome = ev.outcomes[index](this, r)
        map.room(currentRoomId)?.cleared = true
    }

    fun leaveEvent() { maybeAdvanceFloor() }

    // ------------------------------------------------------------ serialization

    /** Full run bundle: everything needed to resume exactly where we left off. */
    fun serialize(): String = buildString {
        append(SAVE_VERSION).append('|').append(seed).append('|').append(characterId).append('|')
        append(difficulty.id).append('|').append(floorNumber).append('|').append(currentRoomId).append('|')
        append(stage.name).append('|').append(if (ended) 1 else 0).append('|').append(if (won) 1 else 0).append('|')
        append(if (restUsed) 1 else 0).append('|')
        append(masterRng.state).append('|')
        append(map.serialize()).append('|')
        append(player.saveToString()).append('|')
        append(stats.serialize()).append('|')
        append(rewardOffers.joinToString(",")).append('|')
        append(shopStock.joinToString(",")).append('|')
        append(shopPrices.joinToString(",")).append('|')
        append(eventId).append('|').append(eventOutcome)
        // combat block appended by caller via combatSave field (kept separate to avoid '|' clash)
    }

    var pendingCombatSave: CombatEngine.CombatSave? = null

    fun serializeWithCombat(): String {
        val c = combat?.saveState()
        val head = serialize()
        if (c == null) return head + "|~|"
        val enc = { s: String -> s.replace("|", "%7C").replace("~", "%7E") }
        return head + "|~|" + enc(c.phase) + ":" + enc(c.player) + ":" + enc(c.board) + ":" + enc(c.enemies) + ":" + enc(c.counters)
    }

    companion object {
        const val SAVE_VERSION = 1

        fun deserialize(data: String): RunState? {
            return try {
            val sep = data.indexOf("|~|")
            val head = if (sep >= 0) data.substring(0, sep) else data
            val combatPart = if (sep >= 0 && data.length > sep + 3) data.substring(sep + 3) else ""
            val p = head.split("|")
            if (p[0].toIntOrNull() != SAVE_VERSION) return null
            val fm = FloorMap.deserialize(p[11]) ?: return null
            val loadedPlayer = PlayerState.loadFromString(p[12])
            val rs = RunState(p[1].toLong(), p[2], Difficulty.byId(p[3]), prebuiltMap = fm, prebuiltPlayer = loadedPlayer)
            rs.floorNumber = p[4].toInt()
            rs.currentRoomId = p[5].toInt()
            rs.stage = runCatching { RunStage.valueOf(p[6]) }.getOrElse { RunStage.MAP }
            rs.ended = p[7] == "1"; rs.won = p[8] == "1"
            rs.restUsed = p[9] == "1"
            // restore master rng state
            val msField = SeededRng::class.java.getDeclaredField("state")
            msField.isAccessible = true
            msField.setLong(rs.masterRng, p[10].toLong())
            rs.stats.restore(p[13])
            if (p.size > 14 && p[14].isNotBlank()) rs.rewardOffers.addAll(p[14].split(","))
            if (p.size > 15 && p[15].isNotBlank()) rs.shopStock.addAll(p[15].split(","))
            if (p.size > 16 && p[16].isNotBlank()) rs.shopPrices.addAll(p[16].split(",").mapNotNull { it.toIntOrNull() })
            if (p.size > 17) rs.eventId = p[17]
            if (p.size > 18) rs.eventOutcome = p[18]
            if (combatPart.isNotBlank()) {
                val dec = { s: String -> s.replace("%7C", "|").replace("%7E", "~") }
                val cf = combatPart.split(":").map { dec(it) }
                if (cf.size >= 5) {
                    val cs = CombatEngine.CombatSave(cf[0], cf[1], cf[2], cf[3], cf[4])
                    val engine = loadCombat(cs, SeededRng(rs.masterRng.nextLong()), rs.player)
                    if (engine != null) {
                        rs.combat = engine
                        rs.stage = RunStage.COMBAT
                    }
                }
            }
            rs
        } catch (e: Exception) {
            null
        }
        }
    }
}
