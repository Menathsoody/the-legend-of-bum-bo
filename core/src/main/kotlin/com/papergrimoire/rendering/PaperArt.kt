package com.papergrimoire.rendering

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.papergrimoire.board.TileType

/**
 * Procedural "handmade paper" art kit. All textures are drawn into Pixmaps at startup:
 * torn-edge paper panels, cardboard tiles with pencil icons per mana family, doodled
 * character/enemy silhouettes and a text font (LibGDX default bitmap font scaled up —
 * no external TTF assets required, keeps the APK self-contained).
 */
object PaperArt {

    // ---- palette -----------------------------------------------------------
    val PAPER = Color(0.87f, 0.80f, 0.65f, 1f)        // aged paper
    val PAPER_DARK = Color(0.72f, 0.64f, 0.50f, 1f)
    val INK = Color(0.16f, 0.13f, 0.11f, 1f)
    val CARDBOARD = Color(0.62f, 0.45f, 0.28f, 1f)

    fun tileColor(t: TileType): Color = when (t) {
        TileType.SHARD -> Color(0.85f, 0.25f, 0.22f, 1f)     // red glass shards
        TileType.CLAY -> Color(0.45f, 0.65f, 0.30f, 1f)      // wet green clay
        TileType.SPORE -> Color(0.60f, 0.40f, 0.75f, 1f)     // violet spores
        TileType.CRYSTAL -> Color(0.30f, 0.55f, 0.85f, 1f)   // blue crystals
        TileType.SPARK -> Color(0.92f, 0.78f, 0.25f, 1f)     // yellow sparks
        TileType.HEART -> Color(0.90f, 0.45f, 0.55f, 1f)
        TileType.CURSE -> Color(0.20f, 0.18f, 0.22f, 1f)
        TileType.WILD -> Color(0.95f, 0.95f, 0.90f, 1f)
        TileType.SEALED -> Color(0.55f, 0.45f, 0.35f, 1f)
    }

    /** deterministic pseudo-random for stable textures */
    private fun h(seed: Int): Float {
        var x = seed * 374761393
        x = (x xor (x shr 13)) * 1274126177
        x = x xor (x shr 16)
        return (x and 0xFFFF) / 65535f
    }

    // ---- texture cache -----------------------------------------------------
    private val cache = HashMap<String, Texture>()
    private var font: BitmapFont? = null
    private val layout = GlyphLayout()

    fun getTexture(key: String): Texture = cache.getOrPut(key) { buildTexture(key) }

    fun uiFont(): BitmapFont = font ?: buildFont().also { font = it }

    fun dispose() {
        cache.values.forEach { it.dispose() }; cache.clear()
        font?.dispose(); font = null
    }

    // ---- font --------------------------------------------------------------

    private fun buildFont(): BitmapFont {
        // Built-in LibGDX 15px font, upscaled — always available on every backend.
        return BitmapFont().apply { data.setScale(1.9f, 1.9f) }
    }

    // ---- text helpers ------------------------------------------------------

    /** Draw text at baseline (x, y). scale multiplies the base 1.9x upscale. */
    fun text(batch: Batch, str: String, x: Float, y: Float, scale: Float = 1f, color: Color = INK) {
        val f = uiFont()
        f.color = color
        f.data.setScale(1.9f * scale.coerceAtLeast(0.3f), 1.9f * scale.coerceAtLeast(0.3f))
        f.draw(batch, str, x, y)
        f.data.setScale(1.9f, 1.9f)
    }

    fun textWidth(str: String, scale: Float = 1f): Float {
        val f = uiFont()
        f.data.setScale(1.9f * scale.coerceAtLeast(0.3f), 1.9f * scale.coerceAtLeast(0.3f))
        layout.setText(f, str)
        f.data.setScale(1.9f, 1.9f)
        return layout.width
    }

    fun textCenter(batch: Batch, str: String, cx: Float, cy: Float, scale: Float, color: Color) {
        text(batch, str, cx - textWidth(str, scale) / 2f, cy + scale * 10f, scale, color)
    }

    // ---- shapes ------------------------------------------------------------

    /** Torn-edge filled paper rectangle with soft drop shadow. */
    fun paperPanel(batch: Batch, x: Float, y: Float, w: Float, hh: Float, seed: Int, tint: Color = PAPER) {
        val tex = getTexture("panel_${w.toInt()}x${hh.toInt()}_$seed")
        batch.setColor(tint)
        // drop shadow
        batch.setColor(0f, 0f, 0f, 0.25f)
        batch.draw(getTexture("shadow"), x + 5, y - 6, w, hh)
        batch.setColor(tint)
        batch.draw(tex, x, y, w, hh)
        batch.setColor(1f, 1f, 1f, 1f)
    }

    fun fillRect(batch: Batch, x: Float, y: Float, w: Float, hh: Float, color: Color) {
        batch.setColor(color)
        batch.draw(getTexture("white"), x, y, w, hh)
        batch.setColor(1f, 1f, 1f, 1f)
    }

    fun line(batch: Batch, x1: Float, y1: Float, x2: Float, y2: Float, thick: Float, color: Color) {
        val dx = x2 - x1; val dy = y2 - y1
        val len = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        batch.setColor(color)
        val ang = Math.atan2(dy.toDouble(), dx.toDouble()).toFloat() * 57.29578f
        // rotated textured quad via the Texture-region overload (Batch has no Texture rotation overload)
        val reg = com.badlogic.gdx.graphics.g2d.TextureRegion(getTexture("white"))
        batch.draw(reg, x1, y1 - thick / 2, 0f, thick / 2, len, thick, 1f, 1f, ang)
        batch.setColor(1f, 1f, 1f, 1f)
    }

    /** Doodle-style circle outline (pencil ring). */
    fun ring(batch: Batch, cx: Float, cy: Float, r: Float, color: Color, thick: Float = 2f) {
        val steps = 18
        for (i in 0 until steps) {
            val a0 = i / steps.toFloat() * (Math.PI * 2).toFloat()
            val a1 = (i + 1) / steps.toFloat() * (Math.PI * 2).toFloat()
            val wob = (h(i * 31 + r.toInt()) - 0.5f) * r * 0.12f
            line(batch, cx + Math.cos(a0.toDouble()).toFloat() * (r + wob), cy + Math.sin(a0.toDouble()).toFloat() * (r + wob),
                 cx + Math.cos(a1.toDouble()).toFloat() * (r + wob), cy + Math.sin(a1.toDouble()).toFloat() * (r + wob), thick, color)
        }
    }

    // ---- texture builders --------------------------------------------------

    private fun buildTexture(key: String): Texture {
        val px = when {
            key == "white" -> solidPixmap(4, 4)
            key == "shadow" -> shadowPixmap(64, 64)
            key.startsWith("panel_") -> panelFromKey(key)
            key.startsWith("tile_") -> tileIcon(key.removePrefix("tile_"))
            key.startsWith("doll_") -> paperDoll(key.removePrefix("doll_"))
            key == "cursor" -> cursorPixmap()
            key == "frame" -> framePixmap()
            else -> solidPixmap(4, 4)
        }
        val tex = Texture(px)
        tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Linear)
        px.dispose()
        return tex
    }

    private fun solidPixmap(w: Int, hh: Int): Pixmap {
        val p = Pixmap(w, hh, Pixmap.Format.RGBA8888)
        p.setColor(1f, 1f, 1f, 1f); p.fill()
        return p
    }

    private fun shadowPixmap(w: Int, hh: Int): Pixmap {
        val p = Pixmap(w, hh, Pixmap.Format.RGBA8888)
        p.setColor(0f, 0f, 0f, 0f); p.fill()
        // blurred-ish dark blob
        for (i in 0 until w * hh / 8) {
            val x = (h(i * 7) * w).toInt(); val y = (h(i * 13) * hh).toInt()
            p.setColor(0f, 0f, 0f, 0.35f); p.fillRectangle(x, y, 2, 2)
        }
        return p
    }

    private fun panelFromKey(key: String): Pixmap {
        // panel_WxH_seed
        val body = key.removePrefix("panel_")
        val dims = body.substringBefore("_")
        val w = dims.substringBefore("x").toIntOrNull() ?: 64
        val hh = dims.substringAfter("x").toIntOrNull() ?: 64
        val seed = body.substringAfter("_").toIntOrNull() ?: 1
        val p = Pixmap(w, hh, Pixmap.Format.RGBA8888)
        p.setColor(1f, 1f, 1f, 1f)
        // torn edges: irregular border via small notches cut out (alpha 0)
        p.fill()
        p.setColor(0f, 0f, 0f, 0f)
        val step = 4
        for (x in 0 until w step step) {
            val topCut = 1 + (h(x * 3 + seed) * 3).toInt()
            val botCut = 1 + (h(x * 5 + seed * 7) * 3).toInt()
            p.drawPixel(x, 0)
            for (dy in 0 until topCut) p.drawPixel(x.coerceIn(0, w - 1), dy.coerceIn(0, hh - 1))
            for (dy in 0 until botCut) p.drawPixel(x.coerceIn(0, w - 1), (hh - 1 - dy).coerceIn(0, hh - 1))
        }
        for (y in 0 until hh step step) {
            val lCut = 1 + (h(y * 11 + seed) * 3).toInt()
            val rCut = 1 + (h(y * 17 + seed * 3) * 3).toInt()
            for (dx in 0 until lCut) p.drawPixel(dx.coerceIn(0, w - 1), y)
            for (dx in 0 until rCut) p.drawPixel((w - 1 - dx).coerceIn(0, w - 1), y)
        }
        // fibre speckles
        p.setColor(0.4f, 0.35f, 0.28f, 0.18f)
        for (i in 0 until w * hh / 60) {
            p.fillRectangle((h(i * 31 + seed) * w).toInt(), (h(i * 57 + seed) * hh).toInt(), 1, 1)
        }
        return p
    }

    private fun tileIcon(typeName: String): Pixmap {
        val s = 64
        val p = Pixmap(s, s, Pixmap.Format.RGBA8888)
        val tt = runCatching { TileType.valueOf(typeName.uppercase()) }.getOrElse { TileType.SHARD }
        val col = tileColor(tt)
        fun c(r: Float, g: Float, b: Float, a: Float) = p.setColor(r, g, b, a)
        // cardboard backing
        c(CARDBOARD.r, CARDBOARD.g, CARDBOARD.b, 1f); p.fill()
        c(0.5f, 0.36f, 0.22f, 1f)
        for (i in 0 until 40) p.fillRectangle((h(i * 3) * s).toInt(), (h(i * 5) * s).toInt(), 2, 1)
        // coloured face
        c(col.r, col.g, col.b, 1f); p.fillRectangle(5, 5, s - 10, s - 10)
        // darker pencil outline
        c(INK.r, INK.g, INK.b, 0.9f)
        p.drawLine(5, 5, s - 6, 5); p.drawLine(5, s - 6, s - 6, s - 6)
        p.drawLine(5, 5, 5, s - 6); p.drawLine(s - 6, 5, s - 6, s - 6)
        // icon doodle (drawn white-ish then inked)
        val lr = (col.r * 0.5f + 0.05f); val lg = (col.g * 0.5f + 0.05f); val lb = (col.b * 0.5f + 0.05f)
        c(lr, lg, lb, 1f)
        when (tt) {
            TileType.SHARD -> { // three triangles like broken glass
                p.fillTriangle(32, 12, 20, 40, 34, 36); p.fillTriangle(38, 20, 46, 44, 30, 46)
            }
            TileType.CLAY -> { // lumpy blob
                p.fillCircle(32, 34, 14); p.fillCircle(24, 28, 8); p.fillCircle(40, 28, 7)
            }
            TileType.SPORE -> { // mushroom puff
                p.fillCircle(32, 26, 12); p.fillRectangle(29, 34, 6, 14)
                c(1f, 1f, 1f, 0.5f); p.fillCircle(26, 22, 3); p.fillCircle(38, 24, 2)
            }
            TileType.CRYSTAL -> { // diamond facets
                p.fillTriangle(32, 10, 18, 32, 32, 54); p.fillTriangle(32, 10, 46, 32, 32, 54)
                c(1f, 1f, 1f, 0.35f); p.drawLine(32, 10, 32, 54)
            }
            TileType.SPARK -> { // lightning zigzag
                p.fillTriangle(38, 10, 22, 34, 32, 34); p.fillTriangle(26, 54, 42, 30, 32, 30)
            }
            TileType.HEART -> {
                p.fillCircle(26, 26, 9); p.fillCircle(38, 26, 9); p.fillTriangle(17, 30, 47, 30, 32, 50)
            }
            TileType.CURSE -> { // scribbled knot
                c(0.9f, 0.85f, 0.75f, 1f)
                p.drawLine(18, 18, 46, 46); p.drawLine(46, 18, 18, 46); p.drawCircle(32, 32, 12)
            }
            TileType.WILD -> { // star
                p.fillTriangle(32, 10, 20, 46, 44, 46); p.fillTriangle(32, 54, 18, 22, 46, 22)
            }
            TileType.SEALED -> { // envelope flap + "?"
                c(0.85f, 0.8f, 0.7f, 1f); p.fillRectangle(12, 16, 40, 32)
                c(INK.r, INK.g, INK.b, 1f); p.drawLine(12, 48, 32, 30); p.drawLine(52, 48, 32, 30)
            }
        }
        return p
    }

    /** Simple hand-drawn silhouette doll per entity id (stable hash-based features). */
    private fun paperDoll(id: String): Pixmap {
        val s = 96
        val p = Pixmap(s, s, Pixmap.Format.RGBA8888)
        p.setColor(0f, 0f, 0f, 0f); p.fill()
        var hash = 1125899906842597L
        for (ch in id) hash = hash * 31 + ch.code
        fun rh(i: Int): Float = h((hash + i * 7919L).toInt())
        val bodyCol = Color(rh(1) * 0.5f + 0.3f, rh(2) * 0.5f + 0.25f, rh(3) * 0.5f + 0.2f, 1f)
        // paper body cutout
        p.setColor(bodyCol.r, bodyCol.g, bodyCol.b, 1f)
        p.fillRectangle(30, 34, 36, 44)             // torso
        p.fillCircle(48, 30, 16)                     // head
        // arms/legs stick figures
        p.fillRectangle(20, 40, 10, 8); p.fillRectangle(66, 40, 10, 8)
        p.fillRectangle(34, 8, 8, 26); p.fillRectangle(54, 8, 8, 26)
        // ink outline
        p.setColor(INK.r, INK.g, INK.b, 1f)
        p.drawRectangle(30, 34, 36, 44)
        p.drawCircle(48, 30, 16)
        // eyes (doodle dots) + mouth
        p.fillCircle(42, 34, 2); p.fillCircle(54, 34, 2)
        if (rh(4) > 0.5f) p.drawLine(44, 24, 52, 24) else p.drawLine(44, 26, 52, 22)
        // hat / horn variation
        when ((rh(5) * 4).toInt()) {
            0 -> { p.fillTriangle(36, 46, 60, 46, 48, 62) }          // pointy hat
            1 -> { p.fillRectangle(38, 48, 20, 4); p.fillRectangle(44, 52, 8, 8) } // crown
            2 -> { p.drawLine(36, 46, 30, 58); p.drawLine(60, 46, 66, 58) }        // horns
            else -> Unit                                              // bald
        }
        // stitched patch
        p.setColor(0f, 0f, 0f, 0.35f)
        p.drawLine(36, 60, 58, 56)
        return p
    }

    private fun cursorPixmap(): Pixmap {
        val s = 48
        val p = Pixmap(s, s, Pixmap.Format.RGBA8888)
        p.setColor(0f, 0f, 0f, 0f); p.fill()
        p.setColor(0.95f, 0.85f, 0.3f, 0.85f)
        p.drawCircle(24, 24, 20); p.drawCircle(24, 24, 18)
        return p
    }

    private fun framePixmap(): Pixmap {
        val s = 64
        val p = Pixmap(s, s, Pixmap.Format.RGBA8888)
        p.setColor(0f, 0f, 0f, 0f); p.fill()
        p.setColor(INK.r, INK.g, INK.b, 0.9f)
        p.drawRectangle(1, 1, s - 2, s - 2)
        p.drawRectangle(3, 3, s - 6, s - 6)
        return p
    }

    /** Nine-slice-ish stretched frame helper. */
    fun frame(batch: Batch, x: Float, y: Float, w: Float, hh: Float, color: Color = INK) {
        batch.setColor(color)
        val tex = getTexture("frame")
        batch.draw(tex, x, y, w, hh)
        batch.setColor(1f, 1f, 1f, 1f)
    }

    // ---- extended doodle helpers (used by screens & board renderer) --------

    fun drawTileIcon(batch: Batch, type: TileType, x: Float, y: Float, size: Float) {
        batch.draw(getTexture("tile_${type.name.lowercase()}"), x, y, size, size)
    }

    fun drawDoll(batch: Batch, id: String, x: Float, y: Float, size: Float, tint: Color? = null) {
        if (tint != null) batch.setColor(tint)
        batch.draw(getTexture("doll_$id"), x, y, size, size)
        batch.setColor(1f, 1f, 1f, 1f)
    }

    /** Filled doodle circle (pencil blob). */
    fun fillCircle(batch: Batch, cx: Float, cy: Float, r: Float, color: Color) {
        ring(batch, cx, cy, r * 0.55f, color, thick = r)
    }

    /** Wobbly hand-drawn star for legendary items / crits. */
    fun star(batch: Batch, cx: Float, cy: Float, r: Float, color: Color) {
        for (i in 0 until 5) {
            val a0 = (i * 2 % 5) / 5f * (Math.PI * 2).toFloat() - Math.PI.toFloat() / 2f
            val a1 = (((i * 2 + 2) % 5)) / 5f * (Math.PI * 2).toFloat() - Math.PI.toFloat() / 2f
            line(batch, cx + Math.cos(a0.toDouble()).toFloat() * r, cy + Math.sin(a0.toDouble()).toFloat() * r,
                 cx + Math.cos(a1.toDouble()).toFloat() * r, cy + Math.sin(a1.toDouble()).toFloat() * r, 2f, color)
        }
    }

    /** Health-bar style bar with pencil frame over paper backing. */
    fun bar(batch: Batch, x: Float, y: Float, w: Float, hgt: Float, frac: Float, fillColor: Color, backColor: Color = PAPER_DARK) {
        fillRect(batch, x, y, w, hgt, backColor)
        fillRect(batch, x, y, w * frac.coerceIn(0f, 1f), hgt, fillColor)
        frame(batch, x, y, w, hgt, INK)
    }
}
