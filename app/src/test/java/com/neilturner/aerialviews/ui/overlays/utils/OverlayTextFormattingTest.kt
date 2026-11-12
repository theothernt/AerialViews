package com.neilturner.aerialviews.ui.overlays.utils

import com.neilturner.aerialviews.models.enums.ClockType
import com.neilturner.aerialviews.models.enums.NowPlayingFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class OverlayTextFormattingTest {
    @Test
    fun `now playing puts song before artist`() {
        assertEquals(
            "Song · Artist",
            NowPlayingFormatter.format("Artist", "Song", NowPlayingFormat.SONG_ARTIST, shortenTrackName = false),
        )
    }

    @Test
    fun `now playing puts artist before song`() {
        assertEquals(
            "Artist · Song",
            NowPlayingFormatter.format("Artist", "Song", NowPlayingFormat.ARTIST_SONG, shortenTrackName = false),
        )
    }

    @Test
    fun `now playing falls back to whichever field is present`() {
        assertEquals(
            "Artist",
            NowPlayingFormatter.format("Artist", "", NowPlayingFormat.SONG_ARTIST, shortenTrackName = false),
        )
        assertEquals(
            "Song",
            NowPlayingFormatter.format("", "Song", NowPlayingFormat.SONG_ARTIST, shortenTrackName = false),
        )
    }

    @Test
    fun `now playing is blank when the format is disabled`() {
        assertEquals(
            "",
            NowPlayingFormatter.format("Artist", "Song", NowPlayingFormat.DISABLED, shortenTrackName = false),
        )
        assertEquals(
            "",
            NowPlayingFormatter.format("Artist", "Song", null, shortenTrackName = false),
        )
    }

    @Test
    fun `poi resolves the entry whose start time has passed`() {
        val poi = mapOf(0 to "Start", 30 to "Middle", 60 to "End")

        assertEquals("Start", PoiText.at(0, poi))
        assertEquals("Start", PoiText.at(29, poi))
        assertEquals("Middle", PoiText.at(30, poi))
        assertEquals("End", PoiText.at(90, poi))
    }

    @Test
    fun `poi falls back when nothing has started`() {
        assertEquals("Location", PoiText.at(5, mapOf(10 to "Later"), fallback = "Location"))
        assertEquals("Location", PoiText.at(5, emptyMap(), fallback = "Location"))
    }

    @Test
    fun `poi flattens newlines`() {
        assertEquals("A B", PoiText.at(0, mapOf(0 to "A\nB")))
    }

    @Test
    fun `clock pattern follows the type then the system setting`() {
        assertEquals("HH:mm", ClockFormatter.pattern(ClockType.HOUR_24, is24Hour = false))
        assertEquals("h:mm a", ClockFormatter.pattern(ClockType.HOUR_12, is24Hour = true))
        assertEquals("HH:mm", ClockFormatter.pattern(ClockType.DEFAULT, is24Hour = true))
        assertEquals("h:mm a", ClockFormatter.pattern(null, is24Hour = false))
    }

    @Test
    fun `clock formats the time with the pattern`() {
        val time = LocalDateTime.of(2026, 11, 12, 9, 5)

        // Only the 24 hour pattern is asserted: the 12 hour one depends on the JVM locale.
        assertEquals("09:05", ClockFormatter.format(time, ClockFormatter.PATTERN_24_HOUR))
        assertEquals("9:05", ClockFormatter.format(time, "h:mm"))
    }

    @Test
    fun `clock ticks on the minute boundary`() {
        assertEquals(30_000L, ClockFormatter.millisToNextMinute(LocalDateTime.of(2026, 11, 12, 12, 0, 30)))
        assertEquals(60_000L, ClockFormatter.millisToNextMinute(LocalDateTime.of(2026, 11, 12, 12, 0, 0)))
    }
}
