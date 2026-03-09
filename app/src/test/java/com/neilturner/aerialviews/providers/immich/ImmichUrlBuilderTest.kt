package com.neilturner.aerialviews.providers.immich

import android.net.Uri
import androidx.core.net.toUri
import com.neilturner.aerialviews.models.enums.ImmichAuthType
import com.neilturner.aerialviews.models.enums.ImmichImageType
import com.neilturner.aerialviews.models.enums.ImmichVideoType
import com.neilturner.aerialviews.models.prefs.ImmichMediaPrefs
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class ImmichUrlBuilderTest {
    private val server = "http://example.com"
    private lateinit var prefs: ImmichMediaPrefs
    private lateinit var builder: ImmichUrlBuilder
    private lateinit var mockUri: Uri

    @BeforeEach
    fun setup() {
        prefs = mockk(relaxed = true)
        builder = ImmichUrlBuilder(server, prefs)

        mockUri = mockk()
        mockkStatic("androidx.core.net.UriKt")
        every { any<String>().toUri() } answers {
            val urlString = firstArg<String>()
            every { mockUri.toString() } returns urlString
            mockUri
        }
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `shared link video transcoded`() {
        every { prefs.authType } returns ImmichAuthType.SHARED_LINK
        every { prefs.pathName } returns "share/12345"
        every { prefs.videoType } returns ImmichVideoType.TRANSCODED
        every { prefs.password } returns ""

        val result = builder.getAssetUri("asset1", isVideo = true)
        assertEquals("$server/api/assets/asset1/video/playback?key=12345", result.toString())
    }

    @Test
    fun `shared link video original with password`() {
        every { prefs.authType } returns ImmichAuthType.SHARED_LINK
        every { prefs.pathName } returns "/s/slug678/"
        every { prefs.videoType } returns ImmichVideoType.ORIGINAL
        every { prefs.password } returns "secret"

        val result = builder.getAssetUri("asset2", isVideo = true)
        assertEquals("$server/api/assets/asset2/original?key=slug678&password=secret", result.toString())
    }

    @Test
    fun `shared link image fullsize`() {
        every { prefs.authType } returns ImmichAuthType.SHARED_LINK
        every { prefs.pathName } returns "12345"
        every { prefs.imageType } returns ImmichImageType.FULLSIZE
        every { prefs.password } returns ""

        val result = builder.getAssetUri("asset3", isVideo = false)
        assertEquals("$server/api/assets/asset3/thumbnail?size=fullsize&key=12345", result.toString())
    }

    @Test
    fun `shared link image original`() {
        every { prefs.authType } returns ImmichAuthType.SHARED_LINK
        every { prefs.pathName } returns "12345"
        every { prefs.imageType } returns ImmichImageType.ORIGINAL
        every { prefs.password } returns ""

        val result = builder.getAssetUri("asset4", isVideo = false)
        assertEquals("$server/api/assets/asset4/original?key=12345", result.toString())
    }

    @Test
    fun `api key video original`() {
        every { prefs.authType } returns ImmichAuthType.API_KEY
        every { prefs.videoType } returns ImmichVideoType.ORIGINAL

        val result = builder.getAssetUri("asset4", isVideo = true)
        assertEquals("$server/api/assets/asset4/original", result.toString())
    }

    @Test
    fun `api key image preview`() {
        every { prefs.authType } returns ImmichAuthType.API_KEY
        every { prefs.imageType } returns ImmichImageType.PREVIEW

        val result = builder.getAssetUri("asset5", isVideo = false)
        assertEquals("$server/api/assets/asset5/thumbnail?size=preview", result.toString())
    }

    @Test
    fun `resolved shared key overrides pathname`() {
        every { prefs.authType } returns ImmichAuthType.SHARED_LINK
        every { prefs.pathName } returns "share/12345"
        every { prefs.videoType } returns ImmichVideoType.TRANSCODED
        every { prefs.password } returns ""

        builder.setResolvedSharedKey("resolved999")

        val result = builder.getAssetUri("asset6", isVideo = true)
        assertEquals("$server/api/assets/asset6/video/playback?key=resolved999", result.toString())
    }

    @Test
    fun `throws exception for null auth type`() {
        every { prefs.authType } returns null

        assertThrows<IllegalStateException> {
            builder.getAssetUri("asset7", isVideo = true)
        }
    }
}
