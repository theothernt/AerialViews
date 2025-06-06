package com.neilturner.aerialviews.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName

@DisplayName("Track Name Shortener Tests")
internal class TrackNameShortenerTest {

    @Test
    @DisplayName("Should remove original mix suffix")
    fun testRemoveOriginalMix() {
        val input = "Blinding Lights - Original Mix"
        val expected = "Blinding Lights"
        val result = TrackNameShortener.shortenTrackName(input)
        assertEquals(expected, result)
    }

    @Test
    @DisplayName("Should remove remastered parenthetical")
    fun testRemoveRemastered() {
        val input = "Shape of You (Remastered)"
        val expected = "Shape of You"
        val result = TrackNameShortener.shortenTrackName(input)
        assertEquals(expected, result)
    }

    @Test
    @DisplayName("Should remove explicit bracket content")
    fun testRemoveExplicit() {
        val input = "Bohemian Rhapsody [Explicit]"
        val expected = "Bohemian Rhapsody"
        val result = TrackNameShortener.shortenTrackName(input)
        assertEquals(expected, result)
    }

    @Test
    @DisplayName("Should remove featuring artists with feat.")
    fun testRemoveFeaturing() {
        val input = "Uptown Funk feat. Bruno Mars"
        val expected = "Uptown Funk"
        val result = TrackNameShortener.shortenTrackName(input)
        assertEquals(expected, result)
    }

    @Test
    @DisplayName("Should remove featuring artists with featuring")
    fun testRemoveFeaturingFull() {
        val input = "Hotel California featuring Eagles"
        val expected = "Hotel California"
        val result = TrackNameShortener.shortenTrackName(input)
        assertEquals(expected, result)
    }

    @Test
    @DisplayName("Should handle multiple suffixes")
    fun testMultipleSuffixes() {
        val input = "Someone Like You (Live Version) - Radio Edit"
        val expected = "Someone Like You"
        val result = TrackNameShortener.shortenTrackName(input)
        assertEquals(expected, result)
    }

    @Test
    @DisplayName("Should leave normal track names unchanged")
    fun testNormalTrackName() {
        val input = "Normal Track Name"
        val expected = "Normal Track Name"
        val result = TrackNameShortener.shortenTrackName(input)
        assertEquals(expected, result)
    }

    @Test
    @DisplayName("Should handle complex multiple patterns")
    fun testComplexMultiplePatterns() {
        val input = "Track - Extended Mix (Remastered) [Clean]"
        val expected = "Track"
        val result = TrackNameShortener.shortenTrackName(input)
        assertEquals(expected, result)
    }

    @Test
    @DisplayName("Should preserve original if result would be empty")
    fun testPreserveIfEmpty() {
        val input = "(Just Parentheses)"
        val expected = "(Just Parentheses)"
        val result = TrackNameShortener.shortenTrackName(input)
        assertEquals(expected, result)
    }

    @Test
    @DisplayName("Should handle featuring with dash combination")
    fun testFeaturingWithDash() {
        val input = "Track feat. Artist - Remix"
        val expected = "Track"
        val result = TrackNameShortener.shortenTrackName(input)
        assertEquals(expected, result)
    }

    @Test
    @DisplayName("Should handle empty string gracefully")
    fun testEmptyString() {
        val input = ""
        val expected = ""
        val result = TrackNameShortener.shortenTrackName(input)
        assertEquals(expected, result)
    }

    @Test
    @DisplayName("Should handle whitespace-only string")
    fun testWhitespaceOnly() {
        val input = "   "
        val expected = "   "
        val result = TrackNameShortener.shortenTrackName(input)
        assertEquals(expected, result)
    }

    @Test
    @DisplayName("Should handle case-insensitive featuring")
    fun testCaseInsensitiveFeaturing() {
        val input = "Song FEAT. Artist"
        val expected = "Song"
        val result = TrackNameShortener.shortenTrackName(input)
        assertEquals(expected, result)
    }

    @Test
    @DisplayName("Should handle mixed case featuring")
    fun testMixedCaseFeaturing() {
        val input = "Song FeAtUrInG Artist"
        val expected = "Song"
        val result = TrackNameShortener.shortenTrackName(input)
        assertEquals(expected, result)
    }
}
