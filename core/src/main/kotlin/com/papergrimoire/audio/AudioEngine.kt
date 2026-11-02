package com.papergrimoire.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.RandomXS128
import com.papergrimoire.save.Settings

/**
 * Fully procedural audio: every sound effect and both looping "songs" are synthesised
 * at startup into PCM16 WAV buffers — no external asset files needed, so the APK stays
 * small and there are zero missing-resource crashes. Music is a short generative paper
 * -folk loop (lead + bass) re-scheduled every bar via the game clock.
 */
class AudioEngine {
    private var sounds: MutableMap<String, Sound> = mutableMapOf()
    private var musicLoop: Sound? = null
    private var musicId = -1L
    private val random = RandomXS128(0xA0D10L)

    var settings = Settings()
    private var initialized = false
    private var musicElapsed = 0f
    private var musicPlaying = false
    private var currentTrack = ""

    fun init() {
        if (initialized) return
        initialized = true
        try {
            sounds["match"] = loadWav(tone(listOf(660.0, 880.0), 0.14, decay = true))
            sounds["bigmatch"] = loadWav(arpeggio(listOf(523.0, 659.0, 784.0, 1046.0), 0.09, 0.5))
            sounds["shift"] = loadWav(noiseBurst(0.08, bandFreq = 900.0))
            sounds["spell"] = loadWav(sweep(300.0, 1200.0, 0.25))
            sounds["hurt"] = loadWav(sweep(400.0, 120.0, 0.22))
            sounds["kill"] = loadWav(tone(listOf(200.0, 150.0, 100.0), 0.3, decay = true))
            sounds["coin"] = loadWav(arpeggio(listOf(988.0, 1319.0), 0.08, 0.35))
            sounds["ui"] = loadWav(tone(listOf(520.0), 0.06, decay = true, gain = 0.4))
            sounds["heal"] = loadWav(sweep(500.0, 900.0, 0.3))
            sounds["boss"] = loadWav(sweep(90.0, 60.0, 0.8, gain = 0.8))
            sounds["win"] = loadWav(arpeggio(listOf(523.0, 659.0, 784.0, 1046.0, 1318.0), 0.12, 0.6))
            sounds["lose"] = loadWav(arpeggio(listOf(392.0, 330.0, 262.0, 196.0), 0.16, 0.6))
            musicLoop = loadWav(buildMusicLoop())
        } catch (e: Exception) {
            // audio is non-critical; game must run silently if device refuses playback
        }
    }

    fun sfx(name: String) {
        if (!initialized || settings.muted) return
        sounds[name]?.play(settings.sfxVolume) ?: return
        if (settings.vibration) runCatching { Gdx.input.vibrate(18) }
    }

    fun playMusic(track: String) {
        if (!initialized || track == currentTrack && musicPlaying) return
        currentTrack = track
        stopMusic()
        if (settings.muted) return
        musicLoop?.let {
            musicId = it.loop(settings.musicVolume * 0.45f)
            musicPlaying = true
        }
    }

    fun stopMusic() {
        if (musicPlaying) { runCatching { if (musicId >= 0) musicLoop?.stop(musicId) else musicLoop?.stop() }; musicPlaying = false; musicId = -1L }
    }

    /** Call each frame to keep volume in sync with settings. */
    fun update(dt: Float) {
        musicElapsed += dt
        if (musicPlaying && musicId >= 0) {
            runCatching { musicLoop?.setVolume(musicId, settings.musicVolume * 0.45f) }
        }
    }

    fun applySettings(s: Settings) {
        settings = s
        if (s.muted) stopMusic() else if (currentTrack.isNotEmpty()) { val t = currentTrack; currentTrack = ""; playMusic(t) }
    }

    fun dispose() {
        sounds.values.forEach { runCatching { it.dispose() } }
        runCatching { musicLoop?.dispose() }
        sounds.clear(); musicLoop = null
    }

    // ------------------------------------------------------------------ synth

    private val rate = 22050

    private fun loadWav(pcm: ShortArray): Sound {
        val bytes = java.nio.ByteBuffer.allocate(pcm.size * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (s in pcm) bytes.putShort(s)
        val wav = wavHeader(pcm.size * 2, rate) + ByteArray(0).let { bytes.array() }
        val tmp = Gdx.files.local("tmp_audio_${random.nextLong().and(Long.MAX_VALUE)}.wav")
        tmp.writeBytes(wav, false)
        val snd = Gdx.audio.newSound(tmp)
        tmp.delete()
        return snd
    }

    private fun wavHeader(dataLen: Int, sampleRate: Int): ByteArray {
        val total = 36 + dataLen
        val bb = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        bb.putChar('R'); bb.putChar('I'); bb.putChar('F'); bb.putChar('F')
        bb.putInt(total); bb.putChar('W'); bb.putChar('A'); bb.putChar('V'); bb.putChar('E')
        bb.putInt(16); bb.putShort(1.toShort()); bb.putShort(1.toShort())
        bb.putInt(sampleRate); bb.putInt(sampleRate * 2); bb.putShort(2.toShort()); bb.putShort(16.toShort())
        bb.putChar('d'); bb.putChar('a'); bb.putChar('t'); bb.putChar('a'); bb.putInt(dataLen)
        return bb.array()
    }

    private fun tone(freqs: List<Double>, dur: Double, decay: Boolean = false, gain: Double = 0.5): ShortArray {
        val n = (rate * dur).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i / rate.toDouble()
            val f = freqs[minOf((i.toDouble() / n * freqs.size).toInt(), freqs.size - 1)]
            var v = Math.sin(2 * Math.PI * f * t)
            v += 0.35 * Math.sin(2 * Math.PI * f * 2 * t) // octave overtone: papery pluck
            if (decay) v *= (1.0 - i.toDouble() / n)
            out[i] = (v * gain * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun arpeggio(freqs: List<Double>, noteDur: Double, gain: Double): ShortArray {
        val parts = freqs.map { tone(listOf(it), noteDur * 1.6, decay = true, gain = gain) }
        val total = parts.sumOf { (it.size * 0.7).toInt() }
        val out = ShortArray(total)
        var idx = 0
        for (p in parts) {
            for (i in p.indices) if (idx < total) { out[idx] = p[i]; idx++ }
        }
        return out
    }

    private fun sweep(from: Double, to: Double, dur: Double, gain: Double = 0.5): ShortArray {
        val n = (rate * dur).toInt()
        val out = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val k = i.toDouble() / n
            val f = from + (to - from) * k
            phase += 2 * Math.PI * f / rate
            val v = Math.sin(phase) * (1.0 - k * 0.7)
            out[i] = (v * gain * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun noiseBurst(dur: Double, bandFreq: Double = 800.0): ShortArray {
        val n = (rate * dur).toInt()
        val out = ShortArray(n)
        var last = 0.0
        for (i in 0 until n) {
            val white = random.nextDouble() * 2 - 1
            last = last * 0.6 + white * 0.4 // crude lowpass -> softer rustle
            val env = 1.0 - i.toDouble() / n
            out[i] = (last * env * 0.45 * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    /** 4-bar generative loop: minor-ish paper folk (A minor pentatonic) with bass line. */
    private fun buildMusicLoop(): ShortArray {
        val bpm = 104.0
        val beat = 60.0 / bpm
        val beats = 32
        val melodyNotes = listOf(
            440.0, 0.0, 523.0, 659.0, 587.0, 0.0, 523.0, 440.0,
            392.0, 440.0, 0.0, 523.0, 659.0, 587.0, 523.0, 0.0,
            440.0, 523.0, 659.0, 0.0, 880.0, 659.0, 587.0, 523.0,
            440.0, 0.0, 392.0, 440.0, 523.0, 0.0, 440.0, 0.0,
        )
        val bassNotes = listOf(110.0, 0.0, 110.0, 82.5, 98.0, 0.0, 98.0, 73.5,
                               110.0, 0.0, 110.0, 82.5, 131.0, 0.0, 98.0, 73.5)
        val n = (rate * beat * beats).toInt()
        val mix = DoubleArray(n)
        for (b in 0 until beats) {
            val f = melodyNotes[b % melodyNotes.size]
            if (f > 0) addNote(mix, (b * beat * rate).toInt(), f, beat * 0.9, 0.30)
            val bf = bassNotes[b % bassNotes.size]
            if (bf > 0 && b % 2 == 0) addNote(mix, (b * beat * rate).toInt(), bf, beat * 1.4, 0.22)
        }
        // light shuffle percussion
        for (b in 0 until (beats * 2)) {
            val start = (b * beat * 0.5 * rate).toInt()
            addNoise(mix, start, 0.03, if (b % 2 == 0) 0.10 else 0.05)
        }
        return ShortArray(n) { (mix[it] * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort() }
    }

    private fun addNote(buf: DoubleArray, start: Int, freq: Double, dur: Double, gain: Double) {
        val len = minOf((dur * rate).toInt(), buf.size - start)
        for (i in 0 until len) {
            val t = i / rate.toDouble()
            val env = Interpolation.fade.apply(1f - i.toFloat() / len)
            var v = Math.sin(2 * Math.PI * freq * t)
            v += 0.25 * Math.sin(2 * Math.PI * freq * 3 * t)
            buf[start + i] += v * env * gain
        }
    }

    private fun addNoise(buf: DoubleArray, start: Int, dur: Double, gain: Double) {
        val len = minOf((dur * rate).toInt(), buf.size - start)
        for (i in 0 until len) {
            val env = 1.0 - i.toDouble() / len
            buf[start + i] += (random.nextDouble() * 2 - 1) * env * gain
        }
    }
}
