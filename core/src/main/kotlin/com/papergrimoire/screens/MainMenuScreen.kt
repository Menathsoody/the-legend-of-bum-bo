package com.papergrimoire.screens

import com.badlogic.gdx.graphics.Color
import com.papergrimoire.game.GameContext
import com.papergrimoire.game.RunState

/** Chooses the right screen for a restored run depending on its stage. */
object ScreenRouter {
    fun routeForStage(ctx: GameContext, rs: RunState): BaseScreen = when (rs.stage) {
        com.papergrimoire.game.RunStage.COMBAT -> BattleScreen(ctx)
        com.papergrimoire.game.RunStage.SHOP -> ShopScreen(ctx)
        com.papergrimoire.game.RunStage.REST -> RestScreen(ctx)
        com.papergrimoire.game.RunStage.EVENT -> EventScreen(ctx)
        com.papergrimoire.game.RunStage.REWARD -> RewardScreen(ctx)
        com.papergrimoire.game.RunStage.GAME_OVER -> GameOverScreen(ctx, won = false)
        com.papergrimoire.game.RunStage.VICTORY -> GameOverScreen(ctx, won = true)
        else -> DungeonMapScreen(ctx)
    }
}

/** Title / main menu with continue-new-run detection. */
class MainMenuScreen(ctx: GameContext) : BaseScreen(ctx) {

    private var t = 0f
    private var showAchievements = false

    override fun draw(dt: Float) {
        t += dt
        val sway = Math.sin(t.toDouble()).toFloat() * 3f
        PaperArt.textCenter(batch, "PAPER GRIMOIRE", Design.W / 2, Design.H - 140 + sway, 3.2f, PaperArt.INK)
        PaperArt.textCenter(batch, "a stitched-up roguelike of ink & malice", Design.W / 2, Design.H - 190, 1.1f, PAPER_DARKISH)

        val hasRun = ctx.run != null && !ctx.run!!.ended
        var y = Design.H - 300f
        if (hasRun) {
            button(Design.W / 2 - 170, y, 340, 64, "CONTINUE RUN", accent = Color(0.75f, 0.85f, 0.6f, 1f)) {
                setScreen(ScreenRouter.routeForStage(ctx, ctx.run!!))
            }
            y -= 80
        }
        button(Design.W / 2 - 170, y, 340, 64, "NEW RUN") {
            audio.playMusic("menu"); setScreen(CharacterSelectScreen(ctx))
        }
        y -= 80
        button(Design.W / 2 - 170, y, 340, 64, "COLLECTIONS") { setScreen(CollectionScreen(ctx, CollectionScreen.Tab.ITEMS)) }
        y -= 80
        button(Design.W / 2 - 170, y, 340, 64, "ACHIEVEMENTS & STATS") { setScreen(StatsScreen(ctx)) }
        y -= 80
        button(Design.W / 2 - 170, y, 340, 64, "SETTINGS") { setScreen(SettingsScreen(ctx)) }

        PaperArt.textCenter(batch, "v1.0 — tap anywhere for sound test", Design.W / 2, 40f, 0.8f, PAPER_DARKISH)
    }

    override fun onTap(wx: Float, wy: Float) { /* buttons handle taps; blank tap = ui blip */ }

    private fun setScreen(s: BaseScreen) {
        screenToDispose?.dispose()
        screenToDispose = s
        ctx.audio.sfx("ui")
        // replace ourselves on the Game
        (com.badlogic.gdx.Gdx.app as? com.badlogic.gdx.Game)?.let { game ->
            game.setScreen(s)
        }
    }

    private var screenToDispose: BaseScreen? = null

    companion object {
        val PAPER_DARKISH = Color(0.85f, 0.8f, 0.7f, 0.85f)
    }
}
