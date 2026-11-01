package com.papergrimoire.progression

import com.papergrimoire.characters.CharacterCatalogue
import com.papergrimoire.game.RunState

/**
 * Persistent meta profile: unlocks, lifetime statistics, achievements and run history.
 * Stored separately from the active run so a finished run archives cleanly instead of
 * being resumable. Serialised as key=value lines — tolerant to unknown keys (forward
 * compatibility) and missing keys (migration defaults).
 */
class GameProfile {
    var version: Int = PROFILE_VERSION

    // ---- unlocks -----------------------------------------------------------
    val unlockedCharacters: MutableSet<String> =
        CharacterCatalogue.all.filter { it.unlockHint == null }.map { it.id }.toMutableSet()
    /** Item/spell/artifact ids discovered at least once across all runs. */
    val discoveredContent: MutableSet<String> = mutableSetOf()
    val achievedIds: MutableSet<String> = mutableSetOf()

    // ---- lifetime stats ----------------------------------------------------
    var runsStarted: Int = 0
    var runsWon: Int = 0
    var totalKills: Int = 0
    var totalGold: Int = 0
    var deepestFloorEver: Int = 1
    var totalMatches: Int = 0
    var bossKills: Int = 0

    /** id -> "floor,turns,gold,won" summary of finished runs (capped to last 20). */
    val runHistory: MutableList<String> = mutableListOf()

    fun isCharacterUnlocked(id: String): Boolean = id in unlockedCharacters

    /** Unlock rules driven by lifetime progress. Returns list of newly unlocked ids. */
    fun applyUnlockRules(): List<String> {
        val newly = mutableListOf<String>()
        for (def in CharacterCatalogue.all) {
            if (def.unlockHint == null || def.id in unlockedCharacters) continue
            val ok = when (def.passiveId) {
                // second unlock tier: any completed run; third tier: a victory
                else -> runsStarted >= 1 && (def.maxHp < 60 || runsWon >= 1)
            }
            if (ok) { unlockedCharacters.add(def.id); newly.add(def.id) }
        }
        return newly
    }

    /** Record a finished (or lost) run into history + lifetime totals. */
    fun archiveRun(rs: RunState) {
        runsStarted++ // counted at start elsewhere; here we only archive completion
        if (rs.won) runsWon++
        totalKills += rs.stats.combatsWon
        totalGold += rs.stats.goldEarned
        totalMatches += rs.stats.turnsPlayed
        if (rs.floorNumber > deepestFloorEver) deepestFloorEver = rs.floorNumber
        val entry = "${System.currentTimeMillis()},${rs.characterId},${rs.floorNumber}," +
            "${rs.stats.turnsPlayed},${rs.stats.goldEarned},${if (rs.won) 1 else 0}"
        runHistory.add(entry)
        while (runHistory.size > 20) runHistory.removeAt(0)
        for (id in rs.player.artifacts + rs.player.spells + rs.player.items.keys) discoveredContent.add(id)
        checkAchievements(rs)
        applyUnlockRules()
    }

    private fun award(id: String) {
        if (achievedIds.add(id)) newlyAchieved.add(id)
    }
    /** Drained by the UI to show toast banners. */
    val newlyAchieved: MutableList<String> = mutableListOf()

    private fun checkAchievements(rs: RunState) {
        if (rs.won) award("first_victory")
        if (rs.stats.combatsWon >= 10) award("ten_duels")
        if (rs.stats.goldEarned >= 200) award("gold_hoarder")
        if (deepestFloorEver >= 6) award("halfway_down")
        if (bossKills >= 5) award("boss_hunter")
        if (runsStarted >= 5) award("persistent_scribe")
        if (unlockedCharacters.size >= 4) award("full_cast")
        if (discoveredContent.size >= 50) award("collector")
        if (rs.won && rs.difficulty.name in setOf("HARSH", "BRUTAL")) award("hard_mode_win")
    }

    // ---- serialisation -----------------------------------------------------

    fun serialize(): String = buildString {
        append("version=").append(version).append('\n')
        append("unlockedChars=").append(unlockedCharacters.joinToString(",")).append('\n')
        append("discovered=").append(discoveredContent.joinToString(",")).append('\n')
        append("achievements=").append(achievedIds.joinToString(",")).append('\n')
        append("runsStarted=").append(runsStarted).append('\n')
        append("runsWon=").append(runsWon).append('\n')
        append("totalKills=").append(totalKills).append('\n')
        append("totalGold=").append(totalGold).append('\n')
        append("deepestFloor=").append(deepestFloorEver).append('\n')
        append("totalMatches=").append(totalMatches).append('\n')
        append("bossKills=").append(bossKills).append('\n')
        append("history=").append(runHistory.joinToString(";")).append('\n')
    }

    companion object {
        const val PROFILE_VERSION = 1

        fun deserialize(data: String): GameProfile {
            val p = GameProfile()
            try {
                val map = HashMap<String, String>()
                for (line in data.split("\n")) {
                    val eq = line.indexOf('=')
                    if (eq > 0) map[line.substring(0, eq).trim()] = line.substring(eq + 1).trim()
                }
                fun setOf3(key: String) = (map[key] ?: "").split(",").filter { it.isNotBlank() }.toMutableSet()
                map["version"]?.toIntOrNull()?.let { p.version = it }   // future migrations branch here
                p.unlockedCharacters.clear(); p.unlockedCharacters.addAll(setOf3("unlockedChars").filter { c -> CharacterCatalogue.all.any { it.id == c } })
                // always keep starter characters unlocked even in legacy saves
                for (def in CharacterCatalogue.all) if (def.unlockHint == null) p.unlockedCharacters.add(def.id)
                p.discoveredContent.addAll(setOf3("discovered"))
                p.achievedIds.addAll(setOf3("achievements"))
                p.runsStarted = map["runsStarted"]?.toIntOrNull() ?: 0
                p.runsWon = map["runsWon"]?.toIntOrNull() ?: 0
                p.totalKills = map["totalKills"]?.toIntOrNull() ?: 0
                p.totalGold = map["totalGold"]?.toIntOrNull() ?: 0
                p.deepestFloorEver = map["deepestFloor"]?.toIntOrNull() ?: 1
                p.totalMatches = map["totalMatches"]?.toIntOrNull() ?: 0
                p.bossKills = map["bossKills"]?.toIntOrNull() ?: 0
                p.runHistory.addAll((map["history"] ?: "").split(";").filter { it.isNotBlank() })
            } catch (e: Exception) {
                // Corrupt profile: fall back to defaults rather than crash.
                return GameProfile()
            }
            return p
        }
    }
}
