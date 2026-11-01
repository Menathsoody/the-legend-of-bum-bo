package com.papergrimoire.dungeon

import com.papergrimoire.util.SeededRng

/** Themed area of the dungeon. Each has its own palette + boss pool. */
data class AreaDef(
    val id: String,
    val name: String,
    val subtitle: String,
    val colorHex: Long,
    val paperHex: Long,
    val bossIds: List<String>,
)

object AreaCatalogue {
    val order = listOf(
        AreaDef("cellar", "The Damp Cellar", "rows of ruined barrels and worse ideas", 0x2E2A24FF, 0xC9B892FF, listOf("boss_jamfather", "boss_heretic_toad")),
        AreaDef("bonefen", "The Bone Fen", "a bog where the drowned keep paperwork", 0x23302AFF, 0xB7C4A8FF, listOf("boss_bonecountess", "boss_leviathan_lint")),
        AreaDef("clockwork", "The Pressworks", "stamping presses that print by themselves", 0x2B2630FF, 0xCBB6A0FF, listOf("boss_foreman", "boss_grand_pendulum", "boss_steam_tsar")),
        AreaDef("grimoire", "The Unbound Grimoire", "a library that is actively reviewing you", 0x1F2430FF, 0xBDC6D6FF, listOf("boss_librarian", "boss_rededitor", "boss_firstpen")),
    )
    fun byId(id: String): AreaDef = order.firstOrNull { it.id == id } ?: order[0]
    fun areaForFloor(floor: Int): AreaDef = order[((floor - 1) / FLOORS_PER_AREA).coerceAtMost(order.size - 1)]
    const val FLOORS_PER_AREA = 3
    const val MAX_FLOOR = 12
}

enum class RoomType { COMBAT, ELITE, TREASURE, SHOP, REST, EVENT, BOSS }

data class RoomNode(
    val id: Int,
    val col: Int,
    val row: Int,
    var type: RoomType,
    var visited: Boolean = false,
    var cleared: Boolean = false,
)

/** A generated floor: rooms in columns connected by forward edges (id -> id). */
class FloorMap(
    val floorNumber: Int,
    val areaId: String,
    val cols: Int,
    val rows: Int,
    val rooms: MutableList<RoomNode>,
) {
    val edges: MutableList<Pair<Int, Int>> = mutableListOf()

    fun room(id: Int): RoomNode? = rooms.firstOrNull { it.id == id }
    fun column(col: Int): List<RoomNode> = rooms.filter { it.col == col }.sortedBy { it.row }
    fun outgoing(roomId: Int): List<RoomNode> =
        edges.filter { it.first == roomId }.mapNotNull { room(it.second) }

    fun serialize(): String = buildString {
        append(floorNumber).append('|').append(areaId).append('|').append(cols).append('|').append(rows).append('|')
        append(rooms.joinToString(";") { "${it.id},${it.col},${it.row},${it.type.name},${if (it.visited) 1 else 0},${if (it.cleared) 1 else 0}" }).append('|')
        append(edges.joinToString(";") { "${it.first}-${it.second}" })
    }

    companion object {
        fun deserialize(data: String): FloorMap? = try {
            val p = data.split("|")
            val fm = FloorMap(p[0].toInt(), p[1], p[2].toInt(), p[3].toInt(), mutableListOf())
            if (p.size > 4 && p[4].isNotBlank()) for (rs in p[4].split(";")) {
                val f = rs.split(",")
                fm.rooms.add(RoomNode(f[0].toInt(), f[1].toInt(), f[2].toInt(), RoomType.valueOf(f[3]), f[4] == "1", f[5] == "1"))
            }
            if (p.size > 5 && p[5].isNotBlank()) for (es in p[5].split(";")) {
                val f = es.split("-"); fm.edges.add(f[0].toInt() to f[1].toInt())
            }
            fm
        } catch (e: Exception) { null }
    }
}

/** Deterministic floor generator: same rng state + floor => same map. */
object DungeonGenerator {

    fun generateFloor(floor: Int, rng: SeededRng): FloorMap {
        val area = AreaCatalogue.areaForFloor(floor)
        val isBossFloor = floor % AreaCatalogue.FLOORS_PER_AREA == 0 || floor >= AreaCatalogue.MAX_FLOOR
        val cols = if (isBossFloor) 4 else 5
        val rows = 4
        val rooms = mutableListOf<RoomNode>()
        var id = 0
        val colStarts = IntArray(cols + 1)
        for (c in 0 until cols) {
            colStarts[c] = rooms.size
            val rowCount = when {
                c == 0 -> 2
                isBossFloor && c == cols - 1 -> 1
                else -> rng.nextInt(2, rows + 1)
            }
            val lanes = rng.shuffled((0 until rows).toList()).take(rowCount).sorted()
            for (lane in lanes) rooms.add(RoomNode(id++, c, lane, RoomType.COMBAT))
        }
        colStarts[cols] = rooms.size
        fun colRooms(c: Int) = rooms.subList(colStarts[c], colStarts[c + 1])

        // connect: each room links to 1-2 nearest rooms in next column; every target gets a parent
        val edges = mutableListOf<Pair<Int, Int>>()
        for (c in 0 until cols - 1) {
            val from = colRooms(c); val to = colRooms(c + 1)
            val linkedTargets = HashSet<Int>()
            for (f in from) {
                val sorted = to.sortedBy { kotlin.math.abs(it.row - f.row) }
                val n = if (to.size >= 2) rng.nextInt(1, 3) else 1
                for (t in sorted.take(n.coerceAtMost(sorted.size))) {
                    if (linkedTargets.add(t.id)) edges.add(f.id to t.id)
                }
            }
            for (t in to) if (t.id !in linkedTargets) {
                val nearest = from.minByOrNull { kotlin.math.abs(it.row - t.row) }!!
                edges.add(nearest.id to t.id)
            }
        }

        // assign room types
        for (r in rooms.filter { it.col == cols - 1 }) r.type = if (isBossFloor) RoomType.BOSS else RoomType.TREASURE
        // only middle columns (not first, not last)
        val candidates = rooms.filter { it.col in 1 until cols - 1 }.toMutableList()
        rng.shuffleInPlace(candidates)
        fun take(type: RoomType, count: Int) { repeat(count) { if (candidates.isNotEmpty()) candidates.removeAt(0).type = type } }
        take(RoomType.SHOP, 1)
        take(RoomType.REST, 1)
        take(RoomType.EVENT, if (rng.nextInt(3) == 0) 1 else 0)
        take(RoomType.TREASURE, if (rng.nextInt(4) == 0) 1 else 0)
        take(RoomType.ELITE, if (floor >= 2 && rng.nextInt(2) == 0) 1 else 0)

        val fm = FloorMap(floor, area.id, cols, rows, rooms)
        fm.edges.addAll(edges)
        return fm
    }

    /** Encounter composition for a combat/elite/boss room. Deterministic via rng. */
    fun enemiesFor(map: FloorMap, room: RoomNode, difficulty: Int, rng: SeededRng): List<String> {
        if (room.type == RoomType.BOSS) {
            val area = AreaCatalogue.byId(map.areaId)
            val pool = area.bossIds
            val idx = (map.floorNumber / AreaCatalogue.FLOORS_PER_AREA - 1).coerceAtLeast(0)
            return listOf(pool[(idx + rng.nextInt(pool.size)) % pool.size])
        }
        val pool = com.papergrimoire.enemies.EnemyCatalogue.all.filter { !it.isBoss && map.areaId in it.areaIds }
        if (pool.isEmpty()) return listOf("ratling")
        val weighted = pool.map { it to it.moveWeight.toDouble() }
        val maxCount = when (room.type) { RoomType.ELITE -> 4 else -> 3 } + difficulty
        val count = rng.nextInt(1, maxCount.coerceAtMost(4) + 1)
        val out = ArrayList<String>()
        repeat(count) { out.add(rng.weighted(weighted).id) }
        return out
    }
}
