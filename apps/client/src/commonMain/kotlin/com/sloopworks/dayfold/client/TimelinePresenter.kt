package com.sloopworks.dayfold.client

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

// ADR 0045 — hub-timeline presenter (Phase 1: status computation).
// Pure function: injected clock (nowIso) mirrors feedCards/deriveNow pattern.
// No wall-clock, no side effects — snapshot/property-testable.

enum class StopStatus { Done, Next, Upcoming }
enum class TimelineScale { Day, Hub }

data class PresentedStop(val stop: Stop, val status: StopStatus, val instant: Instant?)

/**
 * Classify each stop relative to [nowIso].
 *
 * Rules (monotonic — once done, always done):
 *  - Done  : stop.done == true OR parseable instant < now
 *  - Next  : first non-done stop in instant-ascending order
 *  - Upcoming : all remaining non-done stops
 *
 * Stops with an unparseable [Stop.at] sort last and default Upcoming (unless author-done).
 * Output order mirrors the original [stops] list order (display order unchanged).
 */
internal fun stopStatuses(stops: List<Stop>, nowIso: String, tz: TimeZone): List<PresentedStop> {
    val now = parseInstantFlexible(nowIso, tz)
    // pair each stop with its parsed instant (null if unparseable)
    val parsed = stops.map { it to parseInstantFlexible(it.at, tz) }

    // sort indices: parseable-by-instant ascending, unparseable last (stable)
    val ordered = parsed.withIndex().sortedWith(
        compareBy({ it.value.second == null }, { it.value.second })
    )

    var nextAssigned = false
    val statusByOrig = HashMap<Int, StopStatus>()
    for ((origIdx, pair) in ordered) {
        val (stop, inst) = pair
        val done = stop.done || (inst != null && now != null && inst < now)
        val status = when {
            done -> StopStatus.Done
            !nextAssigned -> { nextAssigned = true; StopStatus.Next }
            else -> StopStatus.Upcoming
        }
        statusByOrig[origIdx] = status
    }

    return stops.mapIndexed { i, stop ->
        PresentedStop(stop, statusByOrig[i]!!, parsed[i].second)
    }
}

// --- Scale selection, focal day, NOW line (ADR 0045 Phase 1) ---

private fun Stop.hasIntradayTime(): Boolean = at.trim().length > 10  // "YYYY-MM-DD" = 10; longer has a time component

/**
 * Returns the date with the most intraday-timed stops.
 * Tie → the date containing [nowIso] if any, else earliest.
 * If no intraday stops exist, returns today.
 */
internal fun focalDay(tl: Timeline, nowIso: String, tz: TimeZone): LocalDate? {
    val today = parseInstantFlexible(nowIso, tz)?.toLocalDateTime(tz)?.date
    val byDay = tl.stops.filter { it.hasIntradayTime() }
        .mapNotNull { parseInstantFlexible(it.at, tz)?.toLocalDateTime(tz)?.date }
        .groupingBy { it }.eachCount()
    if (byDay.isEmpty()) return today
    val max = byDay.values.max()
    val tied = byDay.filterValues { it == max }.keys
    return tied.firstOrNull { it == today } ?: tied.minOrNull()
}

/**
 * Day if ≥1 intraday-timed stop on the focal day;
 * Hub if stops span >14 days OR ≥3 date-only stops;
 * else Day.
 */
internal fun selectScale(tl: Timeline, nowIso: String, tz: TimeZone): TimelineScale {
    val focal = focalDay(tl, nowIso, tz)
    val intradayOnFocal = tl.stops.any { it.hasIntradayTime() &&
        parseInstantFlexible(it.at, tz)?.toLocalDateTime(tz)?.date == focal }
    if (intradayOnFocal) return TimelineScale.Day
    val dates = tl.stops.mapNotNull { parseInstantFlexible(it.at, tz)?.toLocalDateTime(tz)?.date }.sorted()
    val spanDays = if (dates.size >= 2) dates.first().daysUntil(dates.last()) else 0
    val dateOnlyCount = tl.stops.count { !it.hasIntradayTime() }
    return if (spanDays > 14 || dateOnlyCount >= 3) TimelineScale.Hub else TimelineScale.Day
}

/**
 * Index AFTER which the NOW line sits in [day] (0 = before all stops).
 * Returns null when the focal day is not today.
 */
internal fun nowLineIndex(day: List<PresentedStop>, nowIso: String, tz: TimeZone): Int? {
    val now = parseInstantFlexible(nowIso, tz) ?: return null
    val today = now.toLocalDateTime(tz).date
    val focalMatchesToday = day.any { it.instant?.toLocalDateTime(tz)?.date == today }
    if (!focalMatchesToday) return null
    val lastPast = day.indexOfLast { it.instant != null && it.instant <= now }
    return lastPast + 1   // 0 = before all; day.size = after all
}
