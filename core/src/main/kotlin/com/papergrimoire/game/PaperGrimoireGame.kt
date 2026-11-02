package com.papergrimoire.game

import com.badlogic.gdx.Game
import com.papergrimoire.rendering.PaperArt
import com.papergrimoire.screens.MainMenuScreen

/** LibGDX entry point. Owns the [GameContext] service hub and the screen stack. */
class PaperGrimoireGame : Game() {

    lateinit var ctx: GameContext
        private set

    override fun create() {
        // Warm up the procedural art cache (textures + font need a GL context).
        PaperArt.getTexture("white")
        PaperArt.uiFont()
        ctx = GameContext()
        // If an unfinished run exists, resume exactly where the player left off.
        val resumed = ctx.resumeOrNew()
        screen = if (resumed != null) {
            com.papergrimoire.screens.ScreenRouter.routeForStage(ctx, resumed)
        } else {
            MainMenuScreen(ctx)
        }
    }

    override fun resize(width: Int, height: Int) { screen?.resize(width, height) }

    override fun pause() { ctx.persistRun(); super.pause() }
    override fun resume() { super.resume() }

    override fun dispose() {
        ctx.persistRun()
        screen?.dispose()
        PaperArt.dispose()
        ctx.audio.dispose()
    }
}
