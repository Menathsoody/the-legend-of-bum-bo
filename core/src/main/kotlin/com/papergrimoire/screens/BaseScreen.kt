package com.papergrimoire.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Rectangle
import com.papergrimoire.audio.AudioEngine
import com.papergrimoire.game.GameContext
import com.papergrimoire.rendering.PaperArt

/** Logical design resolution — everything lays out in these units and scales to fit. */
object Design {
    const val W = 1280f
    const val H = 720f
}

/**
 * Shared base for every screen: fixed virtual viewport (Fit), batch/font access,
 * simple tap-button helper and the paper background. Screens are pure code UI —
 * no external skin assets, everything drawn through PaperArt.
 */
abstract class BaseScreen(val ctx: GameContext) : Screen {

    protected val camera = OrthographicCamera()
    protected val batch = SpriteBatch()
    protected val font: BitmapFont get() = PaperArt.uiFont()
    protected val audio: AudioEngine get() = ctx.audio

    /** Buttons registered during draw; hit-tested on touchUp next frame. */
    private data class HitButton(val rect: Rectangle, val id: String, val action: () -> Unit)
    private val hits = ArrayList<HitButton>()
    private var pointerDownXY: Pair<Float, Float>? = null

    protected fun worldTouch(): Pair<Float, Float> {
        val x = Gdx.input.x.toFloat(); val y = Gdx.input.y.toFloat()
        // Gdx y is top-down; convert to our bottom-left ortho space
        return Pair(x * Design.W / Gdx.graphics.width, (Gdx.graphics.height - y) * Design.H / Gdx.graphics.height)
    }

    /** Register a tappable paper button; draws label centered. */
    protected fun button(x: Float, y: Float, w: Float, h: Float, label: String, enabled: Boolean = true,
                         accent: com.badlogic.gdx.graphics.Color? = null, id: String = label, action: () -> Unit) {
        val c = accent ?: PaperArt.PAPER
        if (!enabled) c.a = 0.45f
        PaperArt.paperPanel(batch, x, y, w, h, id.hashCode(), c)
        if (enabled) PaperArt.frame(batch, x + 3, y + 3, w - 6, h - 6, PaperArt.INK)
        PaperArt.textCenter(batch, label, x + w / 2, y + h / 2, if (h > 60f) 1.1f else 0.9f,
            if (enabled) PaperArt.INK else PaperArt.INK.cpy().also { it.a = 0.5f })
        if (enabled) hits.add(HitButton(Rectangle(x, y, w, h), id, action))
    }

    /** Small icon-style square button (no text). */
    protected fun iconButton(x: Float, y: Float, size: Float, seedKey: String, action: () -> Unit) {
        PaperArt.paperPanel(batch, x, y, size, size, seedKey.hashCode())
        PaperArt.frame(batch, x + 2, y + 2, size - 4, size - 4, PaperArt.INK)
        hits.add(HitButton(Rectangle(x, y, size, size), seedKey, action))
    }

    override fun render(dt: Float) {
        camera.position.set(Design.W / 2f, Design.H / 2f, 0f)
        camera.update()
        batch.projectionMatrix = camera.combined
        Gdx.gl.glClearColor(0.11f, 0.09f, 0.08f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        hits.clear()
        batch.begin()
        background()
        drawHud(dt)
        batch.end()
        handleInput()
        batch.begin()
        draw(dt)
        batch.end()
        audio.update(dt)
    }

    /** Input handling happens between the two batches so taps can switch screens safely. */
    private fun handleInput() {
        if (Gdx.input.justTouched()) {
            val (wx, wy) = worldTouch()
            for (b in hits.reversed()) {
                if (b.rect.contains(wx, wy)) {
                    audio.sfx("ui"); b.action(); return
                }
            }
            onTap(wx, wy)
        } else if (Gdx.input.isTouched()) {
            val p = worldTouch()
            if (pointerDownXY == null) pointerDownXY = p
            onDrag(p.first, p.second, true)
        } else {
            if (pointerDownXY != null) {
                val p = pointerDownXY!!
                onDrag(p.first, p.second, false)
                pointerDownXY = null
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.BACK) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) onBack()
    }

    open fun onTap(wx: Float, wy: Float) {}
    /** [started] true when this drag began with pointer down on us (tap-and-hold lines). */
    open fun onDrag(wx: Float, wy: Float, started: Boolean) {}
    open fun onBack() {}

    open fun background() {
        PaperArt.fillRect(batch, 0f, 0f, Design.W, Design.H, DARKBG)
    }

    open fun drawHud(dt: Float) {}
    abstract fun draw(dt: Float)

    override fun resize(w: Int, h: Int) {
        val aspect = w.toFloat() / h
        val target = Design.W / Design.H
        if (aspect > target) {
            camera.viewportHeight = Design.H
            camera.viewportWidth = Design.H * aspect
        } else {
            camera.viewportWidth = Design.W
            camera.viewportHeight = Design.W / aspect
        }
        camera.update()
    }

    override fun pause() { ctx.persistRun() }
    override fun resume() {}
    override fun hide() {}
    override fun dispose() { batch.dispose() }

    companion object {
        val DARKBG = com.badlogic.gdx.graphics.Color(0.11f, 0.09f, 0.08f, 1f)
    }
}
