package com.sloopworks.dayfold.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.sloopworks.dayfold.client.theme.DayfoldTheme
import kotlinx.datetime.TimeZone
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

// ADR 0045 — snapshot + behavioral tests for the day-scale TimelineCard.
// Uses 7 move-in-day stops so doneCount=3, window=[Elevator slot, Lunch, Bookstore run],
// tailCount=1 ("1 more"), which exercises all card sections.
@OptIn(ExperimentalTestApi::class)
class TimelineCardSnapshotTest {

    private val ny = TimeZone.of("America/New_York")

    private fun dayModel(): TimelineCardModel {
        val tl = Timeline(
            tz = "America/New_York",
            stops = listOf(
                Stop("2026-08-24T07:30:00-04:00", "Car loaded"),
                Stop("2026-08-24T08:00:00-04:00", "Keys pickup"),
                Stop("2026-08-24T09:50:00-04:00", "Checked in"),
                Stop("2026-08-24T11:00:00-04:00", "Elevator slot"),
                Stop("2026-08-24T12:30:00-04:00", "Lunch break"),
                Stop("2026-08-24T13:00:00-04:00", "Bookstore run"),
                Stop("2026-08-24T14:00:00-04:00", "Final walkthrough"),
            )
        )
        return presentTimelineCard(tl, "2026-08-24T10:40:00-04:00", ny)!!
    }

    private fun shot(name: String, dark: Boolean) = runComposeUiTest {
        setContent {
            DayfoldTheme(darkTheme = dark) {
                Box(
                    Modifier
                        .width(390.dp)
                        .background(Color(0xFFE9DDD7))
                        .padding(16.dp)
                ) {
                    TimelineCard(dayModel(), onOpen = {})
                }
            }
        }
        val img = onRoot().captureToImage()
        assertTrue(img.width > 0 && img.height > 0, "snapshot has no pixels")
        File("build/snapshots").apply { mkdirs() }
            .let { dir -> ImageIO.write(img.toAwtImage(), "png", File(dir, "$name.png")) }
    }

    // ── Snapshot tests ─────────────────────────────────────────────────────────

    @Test fun dayLight() = shot("timeline-card-day-light", false)
    @Test fun dayDark()  = shot("timeline-card-day-dark",  true)

    // ── Behavioral assertions ─────────────────────────────────────────────────
    // Verify the card renders real content — not just non-empty pixels.

    @Test fun showsDoneCount() = runComposeUiTest {
        setContent { DayfoldTheme { TimelineCard(dayModel(), onOpen = {}) } }
        // 3 stops are before nowIso (07:30, 08:00, 09:50 all < 10:40)
        onNodeWithText("3 done", substring = true).assertExists()
    }

    @Test fun showsNowTimeLabel() = runComposeUiTest {
        setContent { DayfoldTheme { TimelineCard(dayModel(), onOpen = {}) } }
        // clockTime("2026-08-24T10:40:00-04:00") = "10:40" (12-hr no am/pm)
        onNodeWithText("10:40", substring = true).assertExists()
    }

    @Test fun showsWindowedStop() = runComposeUiTest {
        setContent { DayfoldTheme { TimelineCard(dayModel(), onOpen = {}) } }
        // "Elevator slot" is the Next stop — first of the 3-item window
        onNodeWithText("Elevator", substring = true).assertExists()
    }

    @Test fun showsTail() = runComposeUiTest {
        setContent { DayfoldTheme { TimelineCard(dayModel(), onOpen = {}) } }
        // tailCount = 1 ("Final walkthrough" falls outside the 3-item window)
        onNodeWithText("1 more", substring = true).assertExists()
    }

    @Test fun showsProvenanceChip() = runComposeUiTest {
        setContent { DayfoldTheme { TimelineCard(dayModel(), onOpen = {}) } }
        onNodeWithText("Added to this hub").assertExists()
    }
}
