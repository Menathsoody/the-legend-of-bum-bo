package com.papergrimoire.board

import com.papergrimoire.util.SeededRng

/**
 * Core puzzle model: a 4-row x 9-column grid with a one-row preview queue above it.
 *
 * Movement rules (identical everywhere in the game):
 *  - The player shifts an entire ROW horizontally or an entire COLUMN vertically.
 *  - Rows wrap around (cyclic shift). Columns do NOT wrap: shifting a column up moves
 *    tiles up and pushes the top tile into the preview queue; the bottom fills from the
 *    queue's tail. Shifting down pulls from the queue head.
 *  - CURSE tiles never move by player shifts? No — to keep rules unambiguous, curses DO
 *    move with lines but never match and are only removed by clearing adjacent matches,
 *    spells or at end of turn if marked.
 *
 * Matching rule (Match-4+): a horizontal or vertical run of >= 4 cells whose types are
 * "compatible" clears. Compatibility: same base type, WILD matches any base family,
 * HEART matches nothing (it heals when cleared by adjacency/spells), SEALED counts as
 * its hidden type once revealed. Runs that intersect on the same family merge into one
 * group so shared cells are counted once.
 */
class PuzzleBoard(
    val rows: Int = ROWS,
    val cols: Int = COLS,
    private val rng: SeededRng,
    spawnWeights: Map<TileType, Double>? = null,
) {
    /** grid[row][col]; row 0 is the TOP playfield row. */
    val grid: Array<Array<Tile?>> = Array(rows) { arrayOfNulls(cols) }

    /**
     * Preview queue: [PREVIEW_DEPTH] rows x cols of upcoming tiles shown above the board.
     * previewRow 0 is the row closest to the board (next to fall in).
     */
    val preview: Array<Array<Tile?>> = Array(PREVIEW_DEPTH) { arrayOfNulls(cols) }

    private var nextId = 1
    private var weights: Map<TileType, Double> = spawnWeights ?: DEFAULT_WEIGHTS

    init {
        refillAll()
    }

    // ---------------------------------------------------------------- generation

    fun setSpawnWeights(w: Map<TileType, Double>) { weights = w }

    /** Read-only view of current spawn weights (artifacts adjust them). */
    fun currentWeightsForCopy(): Map<TileType, Double> = HashMap(weights)

    private fun randomTile(): Tile {
        val pairs = weights.entries.map { it.key to it.value }
            .filter { it.second > 0.0 }
        val type = if (pairs.isEmpty()) TileType.SHARD else rng.weighted(pairs)
        return Tile(nextId++, type)
    }

    private fun refillAll() {
        for (r in 0 until rows) for (c in 0 until cols) grid[r][c] = randomTile()
        for (pr in 0 until PREVIEW_DEPTH) for (c in 0 until cols) preview[pr][c] = randomTile()
        // Guarantee no pre-existing matches at fresh board: reroll overlapping runs.
        var guard = 0
        while (guard++ < 64) {
            val groups = findMatches()
            if (groups.isEmpty()) break
            for (g in groups) for ((r, c) in g.cells) grid[r][c] = randomTile()
        }
    }

    // ---------------------------------------------------------------- access

    operator fun get(r: Int, c: Int): Tile? = grid[r][c]
    fun isEmptyCell(r: Int, c: Int) = grid[r][c] == null

    // ---------------------------------------------------------------- movement

    /** Cyclic horizontal shift of a row. positive = right. Returns false if invalid. */
    fun shiftRow(row: Int, amount: Int): Boolean {
        if (row !in 0 until rows) return false
        val a = ((amount % cols) + cols) % cols
        if (a == 0) return true // full wrap == no change, still a legal (free) move shape
        val old = grid[row].clone()
        for (c in 0 until cols) grid[row][c] = old[(c - a + cols) % cols]
        return true
    }

    /**
     * Vertical shift of a column interacting with the preview queue (which acts as a
     * per-column FIFO). positive = down: tiles fall, the queue head feeds the top cells
     * and the queue advances. negative = up: tiles rise, the top tiles are pushed back to
     * the FRONT of the queue (so they will re-enter first), bottom fills from fresh tail.
     */
    fun shiftColumn(col: Int, amount: Int): Boolean {
        if (col !in 0 until cols) return false
        if (amount == 0) return true
        val steps = amount.coerceAtMost(rows).coerceAtLeast(-rows)
        val column = ArrayList<Tile?>()
        for (r in 0 until rows) column.add(grid[r][col])
        if (steps > 0) {
            val feed = pullFromQueue(col, steps)
            val newCol = ArrayList<Tile?>()
            for (i in 0 until steps) newCol.add(feed[i])
            for (t in column.subList(0, rows - steps)) newCol.add(t)
            applyColumn(col, newCol)
        } else {
            val s = -steps
            val pushed = ArrayList(column.subList(0, s))
            pushToQueueFront(col, pushed)
            val newCol = ArrayList<Tile?>()
            for (i in s until rows) newCol.add(column[i])
            newCol.addAll(pullFromQueue(col, s)) // tail refill keeps board full
            applyColumn(col, newCol)
        }
        return true
    }

    private fun applyColumn(col: Int, values: List<Tile?>) {
        for (r in 0 until rows) grid[r][col] = values[r]
    }

    /** Consume n tiles from the head of this column's queue; regenerate empty tail slots. */
    private fun pullFromQueue(col: Int, n: Int): List<Tile?> {
        val out = ArrayList<Tile?>()
        for (i in 0 until n) {
            var t: Tile? = null
            for (pr in 0 until PREVIEW_DEPTH) {
                if (preview[pr][col] != null) { t = preview[pr][col]; preview[pr][col] = null; break }
            }
            // collapse queue upward
            for (pr in 0 until PREVIEW_DEPTH - 1) {
                if (preview[pr][col] == null) preview[pr][col] = preview[pr + 1][col]
            }
            if (preview[PREVIEW_DEPTH - 1][col] == null) preview[PREVIEW_DEPTH - 1][col] = randomTile()
            out.add(t ?: randomTile())
        }
        return out
    }

    /** Push tiles back to the front of the column queue (shift-up returns them). */
    private fun pushToQueueFront(col: Int, tiles: List<Tile?>) {
        val existing = ArrayList<Tile?>()
        for (pr in 0 until PREVIEW_DEPTH) preview[pr][col]?.let { existing.add(it) }
        val merged = (tiles.filterNotNull() + existing).take(PREVIEW_DEPTH)
        for (pr in 0 until PREVIEW_DEPTH) {
            preview[pr][col] = merged.getOrNull(pr) ?: randomTile()
        }
    }

    // ---------------------------------------------------------------- matching

    data class MatchGroup(val family: TileType, val cells: List<Pair<Int, Int>>)

    /** Find all maximal runs of >=4 compatible tiles, merging intersecting same-family runs. */
    fun findMatches(): List<MatchGroup> {
        val runs = mutableListOf<MutableList<Pair<Int, Int>>>()
        val fams = mutableListOf<TileType>()

        fun familyOf(t: Tile?): TileType? {
            val tt = t?.type ?: return null
            if (tt == TileType.CURSE || tt == TileType.HEART) return null
            if (t.sealed) return null // sealed tiles hide content: they do not match
            return if (tt == TileType.WILD) null else tt
        }

        // helper: build a run list where wildcards extend neighbours
        fun scanLine(get: (Int) -> Tile?, len: Int, makeCoord: (Int) -> Pair<Int, Int>) {
            var i = 0
            while (i < len) {
                val base = familyOf(get(i))
                if (base == null) { i++; continue }
                var j = i + 1
                while (j < len) {
                    val f2 = familyOf(get(j))
                    if (f2 == base || f2 == null && get(j)?.type == TileType.WILD) j++
                    else break
                    // note: a WILD between two same-family tiles extends the run
                }
                // extend over leading/trailing wilds greedily handled by outer loop
                if (j - i >= 4) {
                    runs.add((i until j).map(makeCoord).toMutableList()); fams.add(base)
                }
                i = j
            }
        }

        for (r in 0 until rows) scanLine({ c -> grid[r][c] }, cols) { c -> r to c }
        for (c in 0 until cols) scanLine({ r -> grid[r][c] }, rows) { r -> r to c }

        // Merge groups sharing cells AND same family (intersecting L/T shapes count once).
        val merged = mutableListOf<MatchGroup>()
        val cellOwner = HashMap<Pair<Int, Int>, Int>()
        for ((idx, run) in runs.withIndex()) {
            val fam = fams[idx]
            val overlapOwners = run.mapNotNull { cellOwner[it] }.distinct()
            val groupId = overlapOwners.firstOrNull { merged[it].family == fam }
            if (groupId != null) {
                val g = merged[groupId]
                val cells = g.cells as MutableList
                for (cell in run) if (!cells.contains(cell)) {
                    cells.add(cell); cellOwner[cell] = groupId
                }
            } else {
                val newIdx = merged.size
                merged.add(MatchGroup(fam, run.toMutableList()))
                for (cell in run) cellOwner[cell] = newIdx
            }
        }
        return merged.filter { it.cells.size >= 4 }
    }

    /** Cells orthogonally adjacent to any given cell set (used for curse/seal clearing). */
    private fun neighborsOf(cells: Set<Pair<Int, Int>>): Set<Pair<Int, Int>> {
        val out = HashSet<Pair<Int, Int>>()
        for ((r, c) in cells) {
            for ((dr, dc) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val nr = r + dr; val nc = c + dc
                if (nr in 0 until rows && nc in 0 until cols && grid[nr][nc] != null) out.add(nr to nc)
            }
        }
        return out
    }

    // ---------------------------------------------------------------- resolution

    /**
     * Result of resolving one cascade step-set; consumed by combat + animation layers.
     */
    data class ResolutionStep(
        val groups: List<MatchGroup>,
        val clearedCells: List<Pair<Int, Int>>,   // cells that were destroyed (for anim)
        val curseDestroyed: List<Pair<Int, Int>>,
        val heartsHealed: Int,
        val sealedRevealed: List<Pair<Int, Int>>,
    )

    /**
     * Clears all current matches, applies adjacency effects (curse tiles next to cleared
     * cells are destroyed; sealed tiles next to cleared cells are revealed; hearts inside
     * cleared zones heal), drops tiles down within columns and refills from the top using
     * the preview queue. Returns the step description. Null-safe when there is no match.
     */
    fun resolveOnce(): ResolutionStep? {
        val groups = findMatches()
        if (groups.isEmpty()) return null
        val cellsToClear = LinkedHashSet<Pair<Int, Int>>()
        for (g in groups) cellsToClear.addAll(g.cells)

        val adj = neighborsOf(cellsToClear)

        // hearts that happen to sit adjacent to cleared cells also pop (heal)
        val hearts = adj.filter { grid[it.first][it.second]?.type == TileType.HEART }
            .filter { it !in cellsToClear }

        // curses adjacent to cleared cells are destroyed
        val curses = adj.filter { grid[it.first][it.second]?.type == TileType.CURSE }

        // sealed tiles adjacent to cleared cells reveal their content
        val revealed = adj.filter { grid[it.first][it.second]?.sealed == true }

        // apply removals
        val clearedList = ArrayList(cellsToClear) + hearts + curses
        for ((r, c) in clearedList) grid[r][c] = null
        for ((r, c) in revealed) grid[r][c] = grid[r][c]!!.unseal(randomHiddenType())

        // gravity: compact each column downward, fill holes from queue head then fresh
        for (c in 0 until cols) {
            var write = rows - 1
            for (r in rows - 1 downTo 0) {
                val t = grid[r][c]
                if (t != null) {
                    if (write != r) { grid[write][c] = t; grid[r][c] = null }
                    write--
                }
            }
            // remaining holes at top filled from preview queue head
            if (write >= 0) {
                val feed = pullFromQueue(c, write + 1)
                for (i in 0..write) grid[i][c] = feed[i] ?: randomTile()
            }
        }
        return ResolutionStep(groups, clearedList, curses, hearts.size, revealed)
    }

    /** Pop a specific cell without gravity (spells that convert tiles use setTile). */

    private fun randomHiddenType(): TileType {
        val roll = rng.nextInt(100)
        return when {
            roll < 8 -> TileType.HEART
            roll < 12 -> TileType.WILD
            roll < 18 -> TileType.CURSE
            else -> rng.pick(TileType.BASE)
        }
    }

    /**
     * Full cascade resolution with safety cap. Returns every step executed.
     */
    fun resolveAll(maxCascades: Int = MAX_CASCADES): List<ResolutionStep> {
        val steps = ArrayList<ResolutionStep>()
        var guard = 0
        while (guard++ < maxCascades) {
            val s = resolveOnce() ?: break
            steps.add(s)
        }
        return steps
    }

    /** Replace a specific cell (spells / boss effects). */
    fun setTile(r: Int, c: Int, tile: Tile?) {
        require(r in 0 until rows && c in 0 until cols)
        grid[r][c] = tile
    }

    fun insertCurse(r: Int, c: Int) {
        if (grid[r][c] != null) grid[r][c] = grid[r][c]!!.copy(type = TileType.CURSE, sealed = false)
    }

    fun sealRandom(count: Int) {
        repeat(count) {
            val r = rng.nextInt(rows); val c = rng.nextInt(cols)
            grid[r][c]?.let { grid[r][c] = it.copy(sealed = true) }
        }
    }

    fun markRandom(count: Int) {
        repeat(count) {
            val r = rng.nextInt(rows); val c = rng.nextInt(cols)
            grid[r][c]?.let { grid[r][c] = it.copy(marked = true) }
        }
    }

    /** Destroy cursed-marked tiles (end of player turn). Returns destroyed cells. */
    fun popMarkedTiles(): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        for (r in 0 until rows) for (c in 0 until cols) {
            val t = grid[r][c]
            if (t != null && t.marked) { out.add(r to c); grid[r][c] = null }
        }
        if (out.isNotEmpty()) {
            for (c in 0 until cols) {
                var write = rows - 1
                for (r in rows - 1 downTo 0) {
                    val t = grid[r][c]
                    if (t != null) { if (write != r) { grid[write][c] = t; grid[r][c] = null }; write-- }
                }
                if (write >= 0) {
                    val feed = pullFromQueue(c, write + 1)
                    for (i in 0..write) grid[i][c] = feed[i] ?: randomTile()
                }
            }
        }
        return out
    }

    /** Count tiles of a family currently on the board (artifacts may read this). */
    fun countType(type: TileType): Int {
        var n = 0
        for (r in 0 until rows) for (c in 0 until cols) if (grid[r][c]?.type == type) n++
        return n
    }

    // ---------------------------------------------------------------- serialisation

    fun serialize(): String {
        val sb = StringBuilder()
        sb.append(rows).append(';').append(cols).append(';').append(nextId).append(';')
        for (r in 0 until rows) for (c in 0 until cols) appendTile(sb, grid[r][c])
        for (pr in 0 until PREVIEW_DEPTH) for (c in 0 until cols) appendTile(sb, preview[pr][c])
        return sb.toString()
    }

    private fun appendTile(sb: StringBuilder, t: Tile?) {
        if (t == null) { sb.append("-;"); return }
        sb.append(t.type.ordinal).append(',').append(t.id)
        if (t.sealed) sb.append(",s")
        if (t.marked) sb.append(",m")
        sb.append(';')
    }

    companion object {
        const val ROWS = 4
        const val COLS = 9
        const val PREVIEW_DEPTH = 2   // two preview rows: next row visible + one beyond
        const val MAX_CASCADES = 25

        val DEFAULT_WEIGHTS: Map<TileType, Double> = mapOf(
            TileType.SHARD to 24.0, TileType.CLAY to 22.0, TileType.SPORE to 20.0,
            TileType.CRYSTAL to 16.0, TileType.SPARK to 14.0, TileType.HEART to 3.0,
            TileType.CURSE to 0.0, TileType.WILD to 1.0, TileType.SEALED to 0.0,
        )

        fun deserialize(data: String, rng: SeededRng): PuzzleBoard {
            val parts = data.split(";")
            require(parts.size >= 3) { "corrupt board data" }
            val rows = parts[0].toInt(); val cols = parts[1].toInt(); val savedNextId = parts[2].toInt()
            require(rows > 0 && cols > 0 && rows <= 12 && cols <= 24) { "corrupt board dimensions" }
            val board = PuzzleBoard(rows, cols, rng)
            val cells = parts.drop(3)
            var idx = 0
            for (r in 0 until rows) for (c in 0 until cols) {
                board.grid[r][c] = parseTile(cells.getOrNull(idx++))
            }
            for (pr in 0 until PREVIEW_DEPTH) for (c in 0 until cols) {
                board.preview[pr][c] = parseTile(cells.getOrNull(idx++)) ?: board.randomTilePublic()
            }
            var maxId = savedNextId
            for (r in 0 until rows) for (c in 0 until cols) board.grid[r][c]?.let { maxId = maxOf(maxId, it.id) }
            setNextId(board, maxId + 1)
            return board
        }

        private fun parseTile(s: String?): Tile? {
            if (s == null || s.isEmpty() || s == "-") return null
            val p = s.split(",")
            if (p.size < 2) return null
            val type = TileType.values()[p[0].toInt()]
            val id = p[1].toInt()
            val sealed = p.contains("s")
            val marked = p.contains("m")
            return Tile(id, type, sealed, marked)
        }

        private fun setNextId(board: PuzzleBoard, value: Int) {
            val f = PuzzleBoard::class.java.getDeclaredField("nextId")
            f.isAccessible = true
            f.setInt(board, value)
        }
    }

    /** Expose a fresh random tile for deserialisation fallbacks and spells. */
    fun randomTilePublic(): Tile = randomTile()
}
