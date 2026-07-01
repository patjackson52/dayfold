package com.sloopworks.dayfold.client
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimelinePresenterScaleTest {
    private val ny = TimeZone.of("America/New_York")

    private fun intraday() = Timeline(tz = "America/New_York", stops = listOf(
        Stop("2026-08-24T08:00:00-04:00", "a"), Stop("2026-08-24T11:00:00-04:00", "b")))

    private fun roadmap() = Timeline(tz = "America/New_York", stops = listOf(
        Stop("2026-05-01", "a"), Stop("2026-06-12", "b"), Stop("2026-07-20", "c"), Stop("2026-09-19", "d")))

    @Test fun `intraday-today selects Day`() {
        assertEquals(TimelineScale.Day, selectScale(intraday(), "2026-08-24T10:00:00-04:00", ny))
    }

    @Test fun `multi-month date-only selects Hub`() {
        assertEquals(TimelineScale.Hub, selectScale(roadmap(), "2026-08-24T10:00:00-04:00", ny))
    }

    @Test fun `now line only on the focal day when it is today`() {
        val day = stopStatuses(intraday().stops, "2026-08-24T10:00:00-04:00", ny)
        assertEquals(1, nowLineIndex(day, "2026-08-24T10:00:00-04:00", ny)) // after stop[0] (08:00), before stop[1] (11:00)
        assertNull(nowLineIndex(day, "2026-09-01T10:00:00-04:00", ny))
    }

    @Test fun `not-today returns null nowLineIndex`() {
        val day = stopStatuses(intraday().stops, "2026-08-24T10:00:00-04:00", ny)
        assertNull(nowLineIndex(day, "2026-09-01T10:00:00-04:00", ny))
    }

    @Test fun `focalDay tie resolved by today`() {
        // Two dates each have 2 intraday stops; one is today → focalDay returns today
        val tl = Timeline(tz = "America/New_York", stops = listOf(
            Stop("2026-08-24T08:00:00-04:00", "a"), Stop("2026-08-24T11:00:00-04:00", "b"),
            Stop("2026-08-25T08:00:00-04:00", "c"), Stop("2026-08-25T11:00:00-04:00", "d")))
        val result = focalDay(tl, "2026-08-24T10:00:00-04:00", ny)
        assertEquals(kotlinx.datetime.LocalDate(2026, 8, 24), result)
    }

    @Test fun `cross-tz same absolute now yields consistent status`() {
        // Same timeline authored with tz = America/New_York.
        // nowIso represents the same absolute moment; evaluate with ny tz (the author's tz).
        // Verifies presenter is tz-injected, not system-tz dependent.
        val tl = Timeline(tz = "America/New_York", stops = listOf(
            Stop("2026-08-24T08:00:00-04:00", "morning"),
            Stop("2026-08-24T14:00:00-04:00", "afternoon")))
        // now = 2026-08-24T10:00-04:00 = 2026-08-24T14:00:00Z
        val nowNy   = "2026-08-24T10:00:00-04:00"  // NY offset
        val nowUtc  = "2026-08-24T14:00:00Z"         // same absolute instant, UTC form

        val statusesNy  = stopStatuses(tl.stops, nowNy,  ny)
        val statusesUtc = stopStatuses(tl.stops, nowUtc, ny)   // still inject ny as tz

        // Both should classify morning=Done, afternoon=Next regardless of how nowIso is expressed
        assertEquals(statusesNy.map { it.status }, statusesUtc.map { it.status })

        // Now line should land at index 1 (after morning) in both cases
        assertEquals(1, nowLineIndex(statusesNy,  nowNy,  ny))
        assertEquals(1, nowLineIndex(statusesUtc, nowUtc, ny))
    }
}
