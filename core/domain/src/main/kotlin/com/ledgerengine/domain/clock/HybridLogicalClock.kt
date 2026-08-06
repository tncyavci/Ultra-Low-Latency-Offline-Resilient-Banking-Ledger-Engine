package com.ledgerengine.domain.clock

/**
 * Hybrid Logical Clock (Kulkarni et al.) used to establish a deterministic,
 * causally-consistent total order over transactions created on a device
 * while it was offline. Wall-clock time provides human-meaningful ordering
 * in the common case; the logical counter breaks ties and guarantees
 * strict monotonicity even under clock skew or repeated calls within the
 * same millisecond.
 *
 * This is the backbone of [com.ledgerengine.domain.conflict.ConflictResolutionEngine]:
 * two transactions can always be totally ordered by comparing their [HlcTimestamp],
 * which is what lets the engine merge an offline queue with the server's
 * authoritative stream without ambiguity.
 */
data class HlcTimestamp(
    val physicalTimeMillis: Long,
    val logicalCounter: Int,
    val nodeId: String,
) : Comparable<HlcTimestamp> {

    override fun compareTo(other: HlcTimestamp): Int {
        physicalTimeMillis.compareTo(other.physicalTimeMillis).let { if (it != 0) return it }
        logicalCounter.compareTo(other.logicalCounter).let { if (it != 0) return it }
        return nodeId.compareTo(other.nodeId)
    }

    /** Stable, lexicographically sortable string form, suitable for DB primary/sort keys. */
    fun toSortableString(): String =
        "%019d.%010d.%s".format(physicalTimeMillis, logicalCounter, nodeId)

    companion object {
        fun parse(value: String): HlcTimestamp {
            val parts = value.split(".")
            require(parts.size == 3) { "Malformed HLC timestamp: $value" }
            return HlcTimestamp(
                physicalTimeMillis = parts[0].toLong(),
                logicalCounter = parts[1].toInt(),
                nodeId = parts[2],
            )
        }
    }
}

class HybridLogicalClock(
    private val nodeId: String,
    private val wallClock: () -> Long = System::currentTimeMillis,
) {
    private var lastPhysical: Long = 0L
    private var counter: Int = 0

    /** Generates the next local timestamp. Not thread-safe by design — callers own a single-writer queue. */
    fun tick(): HlcTimestamp {
        val physicalNow = wallClock()
        counter = if (physicalNow > lastPhysical) 0 else counter + 1
        lastPhysical = maxOf(physicalNow, lastPhysical)
        return HlcTimestamp(lastPhysical, counter, nodeId)
    }

    /** Merges in a timestamp observed from a remote peer/server, preserving causality. */
    fun observe(remote: HlcTimestamp): HlcTimestamp {
        val physicalNow = wallClock()
        val newPhysical = maxOf(physicalNow, lastPhysical, remote.physicalTimeMillis)
        counter = when (newPhysical) {
            lastPhysical, remote.physicalTimeMillis -> maxOf(counter, remote.logicalCounter) + 1
            else -> 0
        }
        lastPhysical = newPhysical
        return HlcTimestamp(lastPhysical, counter, nodeId)
    }
}
