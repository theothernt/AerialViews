package com.neilturner.aerialviews.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class MusicEventTest {
    @Test
    fun `default event is not playing with empty metadata`() {
        val event = MusicEvent()
        assertEquals("", event.artist)
        assertEquals("", event.song)
        assertFalse(event.isPlaying)
    }

    @Test
    fun `track with blank tags reports playing when isPlaying is true`() {
        val event = MusicEvent(artist = "", song = "", isPlaying = true)
        assertTrue(event.isPlaying)
        assertEquals("", event.artist)
        assertEquals("", event.song)
    }

    @Test
    fun `track with metadata reports playing when isPlaying is true`() {
        val event = MusicEvent(artist = "Pink Floyd", song = "Time", isPlaying = true)
        assertTrue(event.isPlaying)
        assertEquals("Pink Floyd", event.artist)
        assertEquals("Time", event.song)
    }

    @Test
    fun `track with metadata reports not playing when isPlaying is false`() {
        val event = MusicEvent(artist = "Pink Floyd", song = "Time", isPlaying = false)
        assertFalse(event.isPlaying)
    }

    @Test
    fun `destructuring retains artist and song as first two components`() {
        val event = MusicEvent(artist = "Daft Punk", song = "Get Lucky", isPlaying = true)
        val (artist, song) = event
        assertEquals("Daft Punk", artist)
        assertEquals("Get Lucky", song)
    }

    @Test
    fun `events with same metadata but different playing state are not equal`() {
        val playing = MusicEvent(artist = "Queen", song = "Radio Ga Ga", isPlaying = true)
        val paused = MusicEvent(artist = "Queen", song = "Radio Ga Ga", isPlaying = false)
        assertNotEquals(playing, paused)
    }
}
