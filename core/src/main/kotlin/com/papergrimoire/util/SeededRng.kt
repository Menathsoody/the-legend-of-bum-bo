package com.papergrimoire.util

/**
 * Deterministic, serialisable RNG (splitmix64). The run seed plus the current state is
 * saved, so a restored game continues producing exactly the same sequence as it would
 * have without interruption.
 */
class SeededRng(seed: Long) {
    var state: Long = seed
        private set

    fun nextLong(): Long {
        state += 0x9E3779B97F4A7C15uL.toLong()
        var z = state
        z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9uL.toLong()
        z = (z xor (z ushr 27)) * 0x94D049BB133111EBuL.toLong()
        return z xor (z ushr 31)
    }

    /** Uniform in [0, until). Requires until > 0. */
    fun nextInt(until: Int): Int {
        require(until > 0) { "until must be positive, was $until" }
        return (nextLong().ushr(11) % until).toInt() // 53-bit -> bounded
    }

    fun nextInt(from: Int, until: Int): Int = from + nextInt(until - from)

    fun nextFloat(): Float = nextLong().ushr(40) / (1 shl 24).toFloat()

    fun nextBoolean(): Boolean = nextInt(2) == 0

    fun <T> pick(list: List<T>): T {
        require(list.isNotEmpty()) { "cannot pick from empty list" }
        return list[nextInt(list.size)]
    }

    fun <T> weighted(pairs: List<Pair<T, Double>>): T {
        require(pairs.isNotEmpty())
        val total = pairs.sumOf { it.second }
        var roll = nextFloat().toDouble() * total
        for ((value, w) in pairs) {
            roll -= w
            if (roll <= 0.0) return value
        }
        return pairs.last().first
    }

    fun shuffleInPlace(list: MutableList<*>) {
        for (i in list.size - 1 downTo 1) {
            val j = nextInt(i + 1)
            @Suppress("UNCHECKED_CAST")
            val mutable = list as MutableList<Any?>
            val tmp = mutable[i]; mutable[i] = mutable[j]; mutable[j] = tmp
        }
    }

    fun <T> shuffled(list: List<T>): List<T> {
        val copy = list.toMutableList()
        shuffleInPlace(copy)
        return copy
    }

    companion object {
        fun randomSeed(): Long = System.nanoTime() xor (Math.random() * Long.MAX_VALUE).toLong()
    }
}
