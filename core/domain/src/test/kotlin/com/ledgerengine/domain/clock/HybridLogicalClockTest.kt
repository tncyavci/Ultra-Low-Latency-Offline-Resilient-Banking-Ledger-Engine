package com.ledgerengine.domain.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridLogicalClockTest {

    @Test
    fun `successive ticks are strictly monotonic even under a frozen wall clock`() {
        var now = 1_000L
        val clock = HybridLogicalClock(nodeId = "device-a", wallClock = { now })

        val t1 = clock.tick()
        val t2 = clock.tick()
        val t3 = clock.tick()

        assertTrue(t1 < t2)
        assertTrue(t2 < t3)
        assertEquals(t1.physicalTimeMillis, t2.physicalTimeMillis)
        assertEquals(t2.logicalCounter + 1, t3.logicalCounter)
    }

    @Test
    fun `ticks advance normally when wall clock progresses`() {
        var now = 1_000L
        val clock = HybridLogicalClock(nodeId = "device-a", wallClock = { now })

        val t1 = clock.tick()
        now = 2_000L
        val t2 = clock.tick()

        assertTrue(t1 < t2)
        assertEquals(0, t2.logicalCounter)
        assertEquals(2_000L, t2.physicalTimeMillis)
    }

    @Test
    fun `observe merges remote clock ahead of local physical time`() {
        var now = 1_000L
        val local = HybridLogicalClock(nodeId = "device-a", wallClock = { now })
        val remote = HlcTimestamp(physicalTimeMillis = 5_000L, logicalCounter = 3, nodeId = "device-b")

        val merged = local.observe(remote)

        assertEquals(5_000L, merged.physicalTimeMillis)
        assertEquals(4, merged.logicalCounter)
        assertTrue(merged > remote)
    }

    @Test
    fun `timestamps round-trip through sortable string encoding`() {
        val original = HlcTimestamp(physicalTimeMillis = 123_456_789L, logicalCounter = 42, nodeId = "device-a")
        val parsed = HlcTimestamp.parse(original.toSortableString())
        assertEquals(original, parsed)
    }

    @Test
    fun `sortable string encoding preserves total order under lexicographic sort`() {
        var now = 1_000L
        val clock = HybridLogicalClock(nodeId = "device-a", wallClock = { now })
        val timestamps = (1..20).map { clock.tick() }
        val byComparable = timestamps.sorted().map { it.toSortableString() }
        val byLexicographic = timestamps.map { it.toSortableString() }.sorted()
        assertEquals(byComparable, byLexicographic)
    }
}
