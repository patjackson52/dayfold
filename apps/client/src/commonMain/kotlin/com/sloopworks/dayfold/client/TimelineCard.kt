package com.sloopworks.dayfold.client

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sloopworks.dayfold.client.theme.LocalDayfoldColors

// ADR 0045 — hub-timeline card composable (Phase 1: day card).
// Task 11a: day branch only; Task 11b adds the Hub/roadmap branch.

@Composable
fun TimelineCard(model: TimelineCardModel, onOpen: () -> Unit) {
    when (model.scale) {
        TimelineScale.Day -> TimelineDayCard(model, onOpen)
        TimelineScale.Hub -> Box(Modifier) { /* TODO 11b roadmap */ }
    }
}

@Composable
private fun TimelineDayCard(model: TimelineCardModel, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        color = cs.surfaceContainerHigh,
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(horizontal = 17.dp).padding(top = 15.dp, bottom = 13.dp)) {
            // "N done" collapsed cap
            if (model.doneCount > 0) {
                DoneCapRow(model.doneCount)
            }
            // NOW marker row
            if (model.nowTimeLabel != null) {
                NowRow(model.nowTimeLabel)
            }
            // Windowed upcoming rows
            model.window.forEachIndexed { idx, ps ->
                val isLastRow = idx == model.window.lastIndex && model.tailCount == 0
                StopRow(ps, isLastRow = isLastRow)
            }
            // Tail: "N more"
            if (model.tailCount > 0) {
                TailRow(model.tailCount)
            }
            // Footer: "Open timeline" + "Added to this hub" chip
            FooterRow(onOpen)
        }
    }
}

// ── Done cap ──────────────────────────────────────────────────────────────────

@Composable
private fun DoneCapRow(doneCount: Int) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Secondary dot with filled check glyph
        Box(
            modifier = Modifier
                .size(13.dp)
                .background(cs.secondary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = DayfoldIcons.Check,
                contentDescription = null,
                tint = cs.onSecondary,
                modifier = Modifier.size(9.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "$doneCount done",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = cs.outlineVariant,
        )
    }
}

// ── NOW marker ────────────────────────────────────────────────────────────────

@Composable
private fun NowRow(nowTimeLabel: String) {
    val cs = MaterialTheme.colorScheme
    val haloColor = cs.primary.copy(alpha = 0.22f)
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Rail dot column (14dp wide to align with stop rows)
        Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .drawBehind {
                        // Static settle-state halo: primary ring at 0.22α (~4dp outset)
                        drawCircle(
                            color = haloColor,
                            radius = size.minDimension / 2 + 4.dp.toPx(),
                        )
                    }
                    .background(cs.primary, CircleShape),
            )
        }
        Spacer(Modifier.width(13.dp))
        // "NOW · HH:MM" pill
        Box(
            modifier = Modifier
                .background(cs.primary, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                text = "NOW · $nowTimeLabel",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.06.sp,
                color = cs.onPrimary,
            )
        }
        Spacer(Modifier.width(13.dp))
        // Gradient trail
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(listOf(cs.primary, Color.Transparent))
                ),
        )
    }
}

// ── Windowed stop row ─────────────────────────────────────────────────────────

@Composable
private fun StopRow(ps: PresentedStop, isLastRow: Boolean) {
    val cs = MaterialTheme.colorScheme
    val stop = ps.stop
    Row(Modifier.fillMaxWidth()) {
        // Rail: dot + connector
        Column(
            modifier = Modifier.width(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (ps.status) {
                StopStatus.Done -> {
                    // Filled secondary dot with check
                    Box(
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .size(10.dp)
                            .background(cs.secondary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = DayfoldIcons.Check,
                            contentDescription = null,
                            tint = cs.onSecondary,
                            modifier = Modifier.size(7.dp),
                        )
                    }
                }
                StopStatus.Next -> {
                    // Filled primary dot
                    Box(
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .size(10.dp)
                            .background(cs.primary, CircleShape),
                    )
                }
                StopStatus.Upcoming -> {
                    // Hollow outline dot
                    Box(
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .size(10.dp)
                            .background(Color.Transparent, CircleShape)
                            .border(1.5.dp, cs.outlineVariant, CircleShape),
                    )
                }
            }
            // Vertical connector (hidden on last row).
            // Fixed height — NOT weight(1f) — so the rail Column stays intrinsic
            // and the card wraps content height rather than consuming parent max height.
            if (!isLastRow) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(20.dp)
                        .padding(vertical = 3.dp)
                        .background(cs.outlineVariant),
                )
            }
        }

        Spacer(Modifier.width(13.dp))

        // Stop content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLastRow) 0.dp else 13.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stop.title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when (ps.status) {
                        StopStatus.Done -> cs.onSurfaceVariant
                        StopStatus.Next -> cs.onSurface
                        StopStatus.Upcoming -> cs.onSurface
                    },
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (ps.status == StopStatus.Next) {
                    Spacer(Modifier.width(7.dp))
                    // "NEXT" pill
                    Box(
                        modifier = Modifier
                            .border(1.dp, cs.primary, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "NEXT",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.04.sp,
                            color = cs.primary,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Time label: parse HH:MM from ISO "...THH:MM:SS±offset", display as h:MM (12-hr, no am/pm)
                val timeLabel = stopTimeLabel(stop.at)
                Text(
                    text = timeLabel,
                    fontSize = 12.5.sp,
                    color = cs.onSurfaceVariant,
                )
                if (stop.attachments.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = DayfoldIcons.Attachment,
                            contentDescription = null,
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = stop.attachments.size.toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ── Tail row ──────────────────────────────────────────────────────────────────

@Composable
private fun TailRow(tailCount: Int) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(14.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(cs.outline, CircleShape),
            )
        }
        Spacer(Modifier.width(13.dp))
        Text(
            text = "$tailCount more",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onSurfaceVariant,
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Parse "HH:MM" from an ISO-8601 [at] string and format as "h:MM" (12-hr, no am/pm),
 * matching the design's clockTime format. Falls back to the raw string on parse failure.
 */
private fun stopTimeLabel(at: String): String {
    val timePart = at.substringAfter("T", "")
        .substringBefore("-")
        .substringBefore("+")
    val parts = timePart.split(":")
    if (parts.size < 2) return at
    val h = parts[0].toIntOrNull() ?: return at
    val m = parts[1].padStart(2, '0')
    val h12 = (h % 12).let { if (it == 0) 12 else it }
    return "$h12:$m"
}

// ── Footer row ────────────────────────────────────────────────────────────────

@Composable
private fun FooterRow(onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalDayfoldColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(top = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // "Open timeline" with arrow
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = "Open timeline",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.primary,
            )
            Icon(
                imageVector = DayfoldIcons.ArrowOutward,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(16.dp),
            )
        }

        // "Added to this hub" provenance chip
        Row(
            modifier = Modifier
                .background(ext.providerChip, RoundedCornerShape(8.dp))
                .border(1.dp, ext.providerChipOutline, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = DayfoldIcons.AutoAwesome,
                contentDescription = null,
                tint = ext.onProviderChip,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = "Added to this hub",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = ext.onProviderChip,
            )
        }
    }
}
