package com.papergrimoire

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.papergrimoire.game.PaperGrimoireGame
import java.io.File

/** Desktop launcher for quick development testing (same save files as would ship). */
fun main() {
    // Point the "external files" storage at a local directory so saves persist between runs.
    val dir = File(System.getProperty("user.home"), ".papergrimoire").apply { mkdirs() }
    System.setProperty("papergrimoire.savedir", dir.absolutePath)
    val config = Lwjgl3ApplicationConfiguration().apply {
        title = "Paper Grimoire"
        setWindowedMode(1280, 720)
        useGL30(false)
        vSync(true)
        windowIcon(
            com.badlogic.gdx.files.FileHandle(File("desktop/icons/icon.png")),
            com.badlogic.gdx.files.FileHandle(File("desktop/icons/icon-64.png"))
        )
    }
    Lwjgl3Application(PaperGrimoireGame(), config)
}
