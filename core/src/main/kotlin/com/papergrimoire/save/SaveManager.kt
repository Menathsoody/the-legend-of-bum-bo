package com.papergrimoire.save

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.papergrimoire.game.RunState
import com.papergrimoire.progression.GameProfile

/**
 * Persistent user settings (volumes, vibration, quality, tutorial state).
 * Saved as key=value lines in the same storage as the profile.
 */
class Settings {
    var musicVolume: Float = 0.7f
    var sfxVolume: Float = 0.9f
    var muted: Boolean = false
    var vibration: Boolean = true
    /** 0 = full effects, 1 = reduced particles for weak devices */
    var lowFx: Boolean = false
    var tutorialShown: Boolean = false
    var lastCharacterId: String = "mortimer"

    fun serialize(): String = buildString {
        append("music=").append(musicVolume).append('\n')
        append("sfx=").append(sfxVolume).append('\n')
        append("muted=").append(if (muted) 1 else 0).append('\n')
        append("vibration=").append(if (vibration) 1 else 0).append('\n')
        append("lowfx=").append(if (lowFx) 1 else 0).append('\n')
        append("tutorial=").append(if (tutorialShown) 1 else 0).append('\n')
        append("lastChar=").append(lastCharacterId).append('\n')
    }

    companion object {
        fun deserialize(data: String): Settings {
            val s = Settings()
            try {
                for (line in data.split("\n")) {
                    val eq = line.indexOf('='); if (eq <= 0) continue
                    val k = line.substring(0, eq).trim(); val v = line.substring(eq + 1).trim()
                    when (k) {
                        "music" -> s.musicVolume = v.toFloatOrNull()?.coerceIn(0f, 1f) ?: s.musicVolume
                        "sfx" -> s.sfxVolume = v.toFloatOrNull()?.coerceIn(0f, 1f) ?: s.sfxVolume
                        "muted" -> s.muted = v == "1"
                        "vibration" -> s.vibration = v == "1"
                        "lowfx" -> s.lowFx = v == "1"
                        "tutorial" -> s.tutorialShown = v == "1"
                        "lastChar" -> if (v.isNotBlank()) s.lastCharacterId = v
                    }
                }
            } catch (_: Exception) { /* defaults on corrupt file */ }
            return s
        }
    }
}

/**
 * Local persistence hub. Uses LibGDX external storage (app-private files dir on Android,
 * home-dir fallback via system property on desktop for dev testing). All writes are
 * atomic: write to *.tmp, verify non-empty, then rename over the target — a crash mid
 * -write can never destroy the previous good save.
 */
class SaveManager {
    private val dir: FileHandle by lazy {
        val prop = System.getProperty("papergrimoire.savedir")
        if (!prop.isNullOrBlank()) Gdx.files.absolute(prop)
        else runCatching { Gdx.files.external("papergrimoire") }
            .getOrElse { Gdx.files.local("saves") }
    }.also { if (!it.exists()) it.mkdirs() }

    private fun file(name: String) = dir.child(name)

    private fun atomicWrite(name: String, data: String): Boolean {
        return try {
            val tmp = file("$name.tmp")
            tmp.writeString(data, false)
            val check = tmp.readString()
            if (check.length != data.length) { tmp.delete(); return false }
            val ok = tmp.renameTo(file(name)) || run { file(name).writeString(data, false); tmp.delete(); true }
            ok
        } catch (e: Exception) {
            false
        }
    }

    // ---- active run --------------------------------------------------------

    fun saveRun(rs: RunState): Boolean = atomicWrite(RUN_FILE, rs.serializeWithCombat())
    fun loadRun(): RunState? = try {
        val f = file(RUN_FILE)
        if (!f.exists()) null else RunState.deserialize(f.readString())
    } catch (e: Exception) { null }

    fun hasActiveRun(): Boolean = runCatching { file(RUN_FILE).exists() }.getOrDefault(false)
    fun clearRun() { runCatching { file(RUN_FILE).delete() } }

    // ---- profile -----------------------------------------------------------

    fun saveProfile(p: GameProfile): Boolean = atomicWrite(PROFILE_FILE, p.serialize())
    fun loadProfile(): GameProfile = GameProfile.deserialize(readStringSafe(PROFILE_FILE))

    // ---- settings ----------------------------------------------------------

    fun saveSettings(s: Settings): Boolean = atomicWrite(SETTINGS_FILE, s.serialize())
    fun loadSettings(): Settings = Settings.deserialize(readStringSafe(SETTINGS_FILE))

    private fun readStringSafe(name: String): String =
        runCatching { val f = file(name); if (f.exists()) f.readString() else "" }.getOrDefault("")

    companion object {
        const val RUN_FILE = "run.sav"
        const val PROFILE_FILE = "profile.dat"
        const val SETTINGS_FILE = "settings.dat"
    }
}
