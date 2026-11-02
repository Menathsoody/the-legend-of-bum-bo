package com.papergrimoire.game

import com.papergrimoire.audio.AudioEngine
import com.papergrimoire.progression.GameProfile
import com.papergrimoire.save.SaveManager
import com.papergrimoire.save.Settings

/**
 * Global (but explicit — no stray globals) service hub shared by all screens.
 * Created once in PaperGrimoireGame and passed to every screen constructor.
 */
class GameContext {
    val saves = SaveManager()
    var profile: GameProfile = saves.loadProfile()
        private set
    var settings: Settings = saves.loadSettings()
        private set
    val audio = AudioEngine()

    /** The active run, if any (also persisted to disk via [persistRun]). */
    var run: RunState? = null

    init {
        audio.init()
        audio.applySettings(settings)
    }

    // ---- persistence helpers ----------------------------------------------

    fun persistRun() {
        val r = run
        if (r == null || r.ended) { saves.clearRun(); return }
        saves.saveRun(r)
    }

    fun saveSettings() { saves.saveSettings(settings); audio.applySettings(settings) }
    fun saveProfile() { saves.saveProfile(profile) }

    fun startNewRun(characterId: String, difficulty: Difficulty): RunState {
        val seed = SeededSeed.next()
        val rs = RunState(seed, characterId, difficulty)
        run = rs
        profile.runsStarted++
        saveProfile()
        persistRun()
        return rs
    }

    fun resumeOrNew(): RunState? {
        val loaded = saves.loadRun()
        if (loaded != null && !loaded.ended) { run = loaded; return loaded }
        if (loaded != null && loaded.ended) { // archive instead of resuming
            profile.archiveRun(loaded); saveProfile(); saves.clearRun()
        }
        return null
    }

    /** Call when a run finishes (win or loss): archives into the profile. */
    fun finishRun() {
        val r = run ?: return
        profile.archiveRun(r)
        profile.applyUnlockRules()
        saveProfile()
        saves.clearRun()
        run = null
    }

    fun resetAllProgress() {
        profile = GameProfile()
        saves.clearRun()
        run = null
        saveProfile()
    }
}

/** Seed source for brand-new runs (time + nano jitter). */
object SeededSeed {
    fun next(): Long = System.currentTimeMillis() xor (System.nanoTime() * 0x9E3779B97F4A7C15uL.toLong())
}
