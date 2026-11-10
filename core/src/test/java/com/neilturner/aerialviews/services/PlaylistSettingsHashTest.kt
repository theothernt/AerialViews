package com.neilturner.aerialviews.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class PlaylistSettingsHashTest {
    private val settings =
        PlaylistSettingsHash.Settings(
            removeDuplicates = true,
            ignoreNonManifestVideos = false,
            shuffleVideos = true,
            shuffleMusic = false,
            repeatMusic = false,
            filterRemote = false,
        )
    private val providers = listOf("LOCAL:local-hash", "REMOTE:remote-hash")

    @Test
    fun `identical settings and providers produce the same hash`() {
        assertEquals(
            PlaylistSettingsHash.build(settings, providers),
            PlaylistSettingsHash.build(settings.copy(), providers.toList()),
        )
    }

    @Test
    fun `every cached setting changes the hash`() {
        val base = PlaylistSettingsHash.build(settings, providers)

        val variants =
            listOf(
                settings.copy(removeDuplicates = false),
                settings.copy(ignoreNonManifestVideos = true),
                settings.copy(shuffleVideos = false),
                settings.copy(shuffleMusic = true),
                settings.copy(repeatMusic = true),
                settings.copy(filterRemote = true),
            )

        variants.forEach { variant ->
            assertNotEquals(base, PlaylistSettingsHash.build(variant, providers), "Variant should change the hash: $variant")
        }
    }

    @Test
    fun `provider changes change the hash`() {
        val base = PlaylistSettingsHash.build(settings, providers)

        assertNotEquals(base, PlaylistSettingsHash.build(settings, listOf("LOCAL:different")))
        assertNotEquals(base, PlaylistSettingsHash.build(settings, providers + "REMOTE:extra"))
    }

    @Test
    fun `cache format version is part of the key`() {
        // Old caches stored a filtered, period-dependent list. Bumping the version invalidates
        // them once when this code ships, without touching the database schema.
        assertEquals(2, PlaylistSettingsHash.CACHE_FORMAT_VERSION)
    }
}
