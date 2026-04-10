package com.neilturner.aerialviews.providers.immich

import android.net.Uri
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.prefs.ImmichAssetPrefs
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ImmichAssetMapperTest {
    @Test
    fun `process assets maps album name into media metadata`() {
        val prefs = mockk<ImmichAssetPrefs>()
        val urlBuilder = mockk<ImmichUrlBuilder>()
        val mapper = ImmichAssetMapper(prefs, urlBuilder)
        val uri = mockk<Uri>()

        every { prefs.includeVideos } returns true
        every { prefs.includePhotos } returns true
        every { urlBuilder.getAssetUri("asset-1", false) } returns uri

        val results =
            mapper.processAssets(
                listOf(
                    Asset(
                        id = "asset-1",
                        originalPath = "/photos/italy/sunset.jpg",
                        albumName = "Italy",
                    ),
                ),
            )

        assertEquals(1, results.media.size)
        assertEquals(
            "Italy",
            results.media
                .single()
                .metadata.albumName,
        )
        assertEquals(AerialMediaSource.IMMICH, results.media.single().source)
        assertEquals(AerialMediaType.IMAGE, results.media.single().type)
    }
}
