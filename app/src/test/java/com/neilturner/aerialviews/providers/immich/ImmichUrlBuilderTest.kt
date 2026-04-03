package com.neilturner.aerialviews.providers.immich

import android.net.Uri
import com.neilturner.aerialviews.models.enums.ImmichAuthType
import com.neilturner.aerialviews.models.enums.ImmichImageType
import com.neilturner.aerialviews.models.enums.ImmichVideoType
import com.neilturner.aerialviews.models.prefs.ImmichUrlPrefs
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class ImmichUrlBuilderTest {
    private val server = "http://example.com"

    private fun createBuilder(
        authType: ImmichAuthType? = null,
        pathName: String = "",
        password: String = "",
        videoType: ImmichVideoType? = null,
        imageType: ImmichImageType? = null,
    ): ImmichUrlBuilder {
        val prefs = mockk<ImmichUrlPrefs>()
        every { prefs.authType } returns authType
        every { prefs.pathName } returns pathName
        every { prefs.password } returns password
        every { prefs.videoType } returns videoType
        every { prefs.imageType } returns imageType

        // Create a mock Uri factory that returns a mock Uri with the expected toString() value
        val uriFactory: (String) -> Uri = { urlString ->
            val mockUri = mockk<Uri>()
            every { mockUri.toString() } returns urlString
            mockUri
        }
        return ImmichUrlBuilder(server, prefs, uriFactory = uriFactory)
    }

    @Test
    fun `shared link video transcoded`() {
        val builder =
            createBuilder(
                authType = ImmichAuthType.SHARED_LINK,
                pathName = "share/12345",
                videoType = ImmichVideoType.TRANSCODED,
                password = "",
            )

        val result = builder.getAssetUri("asset1", isVideo = true)
        assertEquals("$server/api/assets/asset1/video/playback?key=12345", result.toString())
    }

    @Test
    fun `shared link video original with password`() {
        val builder =
            createBuilder(
                authType = ImmichAuthType.SHARED_LINK,
                pathName = "/s/slug678/",
                videoType = ImmichVideoType.ORIGINAL,
                password = "secret",
            )

        val result = builder.getAssetUri("asset2", isVideo = true)
        assertEquals("$server/api/assets/asset2/original?key=slug678&password=secret", result.toString())
    }

    @Test
    fun `shared link image fullsize`() {
        val builder =
            createBuilder(
                authType = ImmichAuthType.SHARED_LINK,
                pathName = "12345",
                imageType = ImmichImageType.FULLSIZE,
                password = "",
            )

        val result = builder.getAssetUri("asset3", isVideo = false)
        assertEquals("$server/api/assets/asset3/thumbnail?size=fullsize&key=12345", result.toString())
    }

    @Test
    fun `shared link image original`() {
        val builder =
            createBuilder(
                authType = ImmichAuthType.SHARED_LINK,
                pathName = "12345",
                imageType = ImmichImageType.ORIGINAL,
                password = "",
            )

        val result = builder.getAssetUri("asset4", isVideo = false)
        assertEquals("$server/api/assets/asset4/original?key=12345", result.toString())
    }

    @Test
    fun `api key video original`() {
        val builder =
            createBuilder(
                authType = ImmichAuthType.API_KEY,
                videoType = ImmichVideoType.ORIGINAL,
            )

        val result = builder.getAssetUri("asset4", isVideo = true)
        assertEquals("$server/api/assets/asset4/original", result.toString())
    }

    @Test
    fun `api key image preview`() {
        val builder =
            createBuilder(
                authType = ImmichAuthType.API_KEY,
                imageType = ImmichImageType.PREVIEW,
            )

        val result = builder.getAssetUri("asset5", isVideo = false)
        assertEquals("$server/api/assets/asset5/thumbnail?size=preview", result.toString())
    }

    @Test
    fun `resolved shared key overrides pathname`() {
        val builder =
            createBuilder(
                authType = ImmichAuthType.SHARED_LINK,
                pathName = "share/12345",
                videoType = ImmichVideoType.TRANSCODED,
                password = "",
            )

        builder.setResolvedSharedKey("resolved999")

        val result = builder.getAssetUri("asset6", isVideo = true)
        assertEquals("$server/api/assets/asset6/video/playback?key=resolved999", result.toString())
    }

    @Test
    fun `throws exception for null auth type`() {
        val builder = createBuilder(authType = null)

        assertThrows<IllegalStateException> {
            builder.getAssetUri("asset7", isVideo = true)
        }
    }
}
