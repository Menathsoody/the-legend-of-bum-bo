package com.papergrimoire.rendering

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Interpolation
import com.papergrimoire.board.PuzzleBoard
import com.papergrimoire.board.TileType

/**
 * Draws the 4x9 puzzle board + preview rows and animates line shifts, match pops
 * and column drops. All animation is time-based (state lives here, not in the
 * game logic) so the PuzzleBoard model stays pure & testable.
 */
class BoardRenderer {

    val cell = 84f
    val gap = 6f
    var originX = 0f; var originY = 0f   // bottom-left of grid[rows-1][0] cell... see layout()

    /** Layout the board centered on a rect. */
    fun layout(cx: Float, cy: Float, board: PuzzleBoard) {
        val w = board.cols * (cell + gap) - gap
        val h = (board.rows + PuzzleBoard.PREVIEW_DEPTH) * (cell + gap) - gap
        originX = cx - w / 2
        originY = cy - h / 2
    }

    private fun cellX(c: Int) = originX + c * (cell + gap)
    private fun cellY(r: Int, rows: Int) = originY + (rows - 1 - r) * (cell + gap) + PuzzleBoard.PREVIEW_DEPTH * (cell + gap)

    // ---- animation queues ----------------------------------------------------

    class PopAnim(var t: Float, val cells: List<Pair<Int, Int>>, val family: TileType) {
        var done = false
    }
    class DropAnim(var t: Float, val moves: List<Triple<Int, Int, Float>>) { // col, row, dropDistance(cells)
        var done = false
    }
    class ShiftAnim(var t: Float, val row: Int, val col: Int, val amount: Int, val isRow: Boolean) {
        var done = false
    }

    val pops = ArrayList<PopAnim>()
    val drops = ArrayList<DropAnim>()
    val shift = ArrayList<ShiftAnim>()
    var floatTexts = ArrayList<FloatText>()

    class FloatText(var t: Float, val x: Float, val y: Float, val text: String, val color: Color)

    fun pushFloat(x: Float, y: Float, text: String, color: Color) { floatTexts.add(FloatText(0f, x, y, text, color)) }

    /** Register animations from a resolution step list produced by CombatEngine.lastResolution. */
    fun registerResolution(board: PuzzleBoard, steps: List<PuzzleBoard.ResolutionStep>) {
        for ((idx, s) in steps.withIndex()) {
            val delay = idx * 0.12f
            pops.add(PopAnim(-delay, s.clearedCells, s.groups.firstOrNull()?.family ?: TileType.SHARD))
        }
    }

    fun registerShift(row: Int, col: Int, amount: Int, isRow: Boolean) {
        shift.add(ShiftAnim(0f, row, col, amount, isRow))
    }

    fun update(dt: Float) {
        for (p in pops) { p.t += dt; if (p.t > 0.35f) p.done = true }
        pops.removeAll { it.done }
        for (d in drops) { d.t += dt; if (d.t > 0.3f) d.done = true }
        drops.removeAll { it.done }
        for (sh in shift) { sh.t += dt; if (sh.t > 0.18f) sh.done = true }
        shift.removeAll { it.done }
        for (f in floatTexts) f.t += dt
        floatTexts.removeAll { it.t > 1.1f }
    }

    fun busy(): Boolean = pops.isNotEmpty() || drops.isNotEmpty() || shift.isNotEmpty()

    // ---- drawing -------------------------------------------------------------

    fun draw(batch: Batch, board: PuzzleBoard, time: Float, highlightRow: Int = -1, highlightCol: Int = -1, dragOffsetPx: Float = 0f, draggingRow: Boolean = true) {
        // board backing panel
        val bw = board.cols * (cell + gap) + 24
        val bh = (board.rows + PuzzleBoard.PREVIEW_DEPTH) * (cell + gap) + 24
        PaperArt.paperPanel(batch, originX - 12, originY - 12, bw, bh, 77, Color(0.55f, 0.42f, 0.30f, 1f))

        // preview rows (dimmer, smaller)
        for (pr in 0 until PuzzleBoard.PREVIEW_DEPTH) {
            for (c in 0 until board.cols) {
                val t = board.preview[pr][c] ?: continue
                val x = cellX(c); val y = originY + (PuzzleBoard.PREVIEW_DEPTH - 1 - pr) * (cell + gap)
                val size = if (pr == 0) cell * 0.72f else cell * 0.5f
                batch.setColor(Color(1f, 1f, 1f, if (pr == 0) 0.85f else 0.5f))
                PaperArt.drawTileIcon(batch, t.type, x + (cell - size) / 2, y + (cell - size) / 2, size)
                batch.setColor(1f, 1f, 1f, 1f)
            }
        }

        val poppingCells = HashSet<Pair<Int, Int>>()
        for (p in pops) for (cc in p.cells) poppingCells.add(cc)

        for (r in 0 until board.rows) for (c in 0 until board.cols) {
            val t = board[r, c] ?: continue
            var x = cellX(c); var y = cellY(r, board.rows)
            var scale = 1f
            var alpha = 1f

            // active drag offset preview
            if (highlightRow == r && draggingRow) x += dragOffsetPx
            if (highlightCol == c && !draggingRow && highlightRow < 0) y += dragOffsetPx

            // shift animation (post-commit visual echo)
            for (sh in shift) {
                if (sh.isRow && sh.row == r) x += Interpolation.smooth.apply(1f - sh.t / 0.18f) * sh.amount * (cell + gap) * -0.25f
                if (!sh.isRow && sh.col == c) y -= Interpolation.smooth.apply(1f - sh.t / 0.18f) * sh.amount.coerceAtMost(board.rows) * (cell + gap) * -0.2f
            }

            if (t.type == TileType.CURSE) {
                x += Math.sin((time * 6 + c).toDouble()).toFloat() * 1.5f
            }
            if (t.marked) {
                PaperArt.ring(batch, x + cell / 2, y + cell / 2, cell * 0.55f, Color(0.95f, 0.3f, 0.2f, 1f), 3f)
            }

            if ((r to c) in poppingCells) {
                val p = pops.first { (r to c) in it.cells }
                val k = (p.t / 0.35f).coerceIn(0f, 1f)
                scale = 1f + k * 0.5f
                alpha = 1f - k
            } else {
                // gentle idle sway per tile (paper feel)
                y += Math.sin((time * 2 + r * 3 + c * 1.7).toDouble()).toFloat() * 1.2f
            }

            val sz = cell * scale
            batch.setColor(Color(1f, 1f, 1f, alpha))
            if (t.sealed) {
                PaperArt.drawTileIcon(batch, TileType.SEALED, x + (cell - sz) / 2, y + (cell - sz) / 2, sz)
                PaperArt.textCenter(batch, "?", x + cell / 2, y + cell / 2, 1.4f, Color(0.2f, 0.15f, 0.1f, alpha))
            } else {
                PaperArt.drawTileIcon(batch, t.type, x + (cell - sz) / 2, y + (cell - sz) / 2, sz)
            }
            batch.setColor(1f, 1f, 1f, 1f)
        }

        // highlight hovered line
        if (highlightRow >= 0) {
            batch.setColor(Color(0.98f, 0.9f, 0.4f, 0.25f))
            PaperArt.fillRect(batch, originX - 4, cellY(highlightRow, board.rows) - 4, board.cols * (cell + gap) + 4, cell + 8, Color(0.98f, 0.9f, 0.4f, 0.28f))
            batch.setColor(1f, 1f, 1f, 1f)
        }
        if (highlightCol >= 0 && highlightRow < 0) {
            PaperArt.fillRect(batch, cellX(highlightCol) - 4, cellY(board.rows - 1, board.rows) - 4, cell + 8, board.rows * (cell + gap) + 4, Color(0.98f, 0.9f, 0.4f, 0.28f))
        }

        // floating combat text
        for (f in floatTexts) {
            val k = f.t / 1.1f
            val a = 1f - k
            PaperArt.textCenter(batch, f.text, f.x, f.y + k * 60f, 1.1f, Color(f.color.r, f.color.g, f.color.b, a))
        }
    }

    /** Convert a world point to board cell coordinates (or -1,-1). */
    fun pickCell(board: PuzzleBoard, wx: Float, wy: Float): Pair<Int, Int> {
        val c = ((wx - originX) / (cell + gap)).toInt()
        if (wx < originX || c !in 0 until board.cols) return -1 to -1
        val gridTop = cellY(0, board.rows) + cell
        val gridBottom = cellY(board.rows - 1, board.rows)
        if (wy > gridTop + gap || wy < gridBottom - gap) return -1 to -1
        val rFromBottom = ((gridTop - wy) / (cell + gap)).toInt()
        val r = rFromBottom.coerceIn(0, board.rows - 1)
        return r to c
    }

    fun cellCenter(board: PuzzleBoard, r: Int, c: Int): Pair<Float, Float> =
        (cellX(c) + cell / 2) to (cellY(r, board.rows) + cell / 2)
}
