package com.sloopworks.dayfold.client

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

// Friendly briefing date, e.g. "Thursday, June 25". Pure (takes a LocalDate) so the
// composable injects today via the clock+timezone and tests pass a fixed date.
fun formatDayLabel(date: LocalDate): String {
  fun title(s: String) = s.lowercase().replaceFirstChar { it.uppercase() }
  return "${title(date.dayOfWeek.name)}, ${title(date.month.name)} ${date.day}"
}

// Calm countdown labels for the Hubs surface (ADR 0006). The API serves DB-shaped
// timestamptz strings ("2026-06-24 07:23:51.41-07"), not ISO — normalize, then
// diff against now. Pure + nowIso-injectable so it's testable without a clock.
// Day math is whole-day truncation (good enough for a badge); not calendar-exact.

internal fun normalizeTs(s: String?): String? {
  if (s.isNullOrBlank()) return null
  var t = s.trim().replace(' ', 'T')
  // ensure a parseable tz offset: "…-07" → "…-07:00"; "…+0530" → "…+05:30"; "Z" ok.
  val tz = Regex("([+-])(\\d{2})(\\d{2})?$").find(t)
  if (tz != null) {
    val (sign, hh, mm) = tz.destructured
    t = t.substring(0, tz.range.first) + "$sign$hh:${if (mm.isEmpty()) "00" else mm}"
  }
  return t
}

private fun parseOrNull(s: String?): Instant? = normalizeTs(s)?.let { runCatching { Instant.parse(it) }.getOrNull() }

// "Today" | "Tomorrow" | "in N days" | "Yesterday" | "N days ago" | null.
// targetIso = the hub's countdown_to ?: start_at; nowIso = an ISO/DB now.
fun countdownLabel(targetIso: String?, nowIso: String): String? {
  val target = parseOrNull(targetIso) ?: return null
  val now = parseOrNull(nowIso) ?: return null
  val days = (target - now).inWholeDays
  return when {
    days == 0L -> "Today"
    days == 1L -> "Tomorrow"
    days > 1L -> "in $days days"
    days == -1L -> "Yesterday"
    else -> "${-days} days ago"
  }
}
