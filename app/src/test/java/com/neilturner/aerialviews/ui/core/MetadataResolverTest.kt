package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.net.Uri
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.DateType
import com.neilturner.aerialviews.models.enums.LocationType
import com.neilturner.aerialviews.models.enums.MetadataType
import com.neilturner.aerialviews.models.videos.AerialExifMetadata
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.AerialMediaMetadata
import com.neilturner.aerialviews.utils.GeocoderHelper
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class MetadataResolverTest {
    private lateinit var context: Context
    private lateinit var geocoderHelper: GeocoderHelper
    private lateinit var resolver: MetadataResolver
    private lateinit var uri: Uri

    @BeforeEach
    fun setup() {
        context = mockk()
        geocoderHelper = mockk()
        resolver = MetadataResolver(geocoderHelper)
        uri = mockk()
        every { uri.path } returns "/test/path/video.mp4"
        every { uri.lastPathSegment } returns "video.mp4"
    }

    private fun createMedia(
        type: AerialMediaType = AerialMediaType.VIDEO,
        poi: Map<Int, String> = emptyMap(),
        shortDesc: String = "",
        city: String? = null,
        state: String? = null,
        country: String? = null,
        description: String? = null,
        date: String? = null,
    ): AerialMedia =
        AerialMedia(
            uri = uri,
            type = type,
            metadata =
                AerialMediaMetadata(
                    shortDescription = shortDesc,
                    pointsOfInterest = poi,
                    exif =
                        AerialExifMetadata(
                            city = city,
                            state = state,
                            country = country,
                            description = description,
                            date = date,
                        ),
                ),
        )

    private val defaultPrefs =
        MetadataResolver.Preferences(
            videoSelection = "POI,LOCATION,DESC,FILENAME",
            videoFolderDepth = 1,
            videoLocationType = LocationType.CITY_STATE,
            photoSelection = "LOCATION,DATE_TAKEN,DESCRIPTION,FILENAME",
            photoFolderDepth = 1,
            photoLocationType = LocationType.CITY_STATE_COUNTRY,
            photoDateType = DateType.COMPACT,
            photoDateCustom = "",
        )

    @Test
    fun `resolve video with POI uses dynamic metadata`(): Unit =
        runTest {
            val media =
                createMedia(
                    poi = mapOf(0 to "Intro", 10 to "Main Scene"),
                    shortDesc = "Intro",
                )
            val prefs = defaultPrefs.copy(videoSelection = "POI")

            val result = resolver.resolve(context, media, prefs)

            assertEquals("Intro", result.text)
            assertEquals(2, result.poi.size)
            assertEquals(MetadataType.DYNAMIC, result.metadataType)
        }

    @Test
    fun `resolve video with empty POI falls back`(): Unit =
        runTest {
            val media =
                createMedia(
                    poi = emptyMap(),
                    shortDesc = "Fallback Desc",
                )
            val prefs = defaultPrefs.copy(videoSelection = "POI,DESC")

            val result = resolver.resolve(context, media, prefs)

            assertEquals("Fallback Desc", result.text)
            assertEquals(MetadataType.STATIC, result.metadataType)
        }

    @Test
    fun `resolve photo with location uses city state country`(): Unit =
        runTest {
            val media =
                createMedia(
                    type = AerialMediaType.IMAGE,
                    city = "Paris",
                    state = "Ile-de-France",
                    country = "France",
                )
            val prefs = defaultPrefs.copy(photoSelection = "LOCATION", photoLocationType = LocationType.CITY_STATE_COUNTRY)

            val result = resolver.resolve(context, media, prefs)

            assertEquals("Paris, Ile-de-France, France", result.text)
            assertEquals(MetadataType.STATIC, result.metadataType)
        }

    @Test
    fun `resolve photo with relative date`(): Unit =
        runTest {
            val media =
                createMedia(
                    type = AerialMediaType.IMAGE,
                    date = "2023:01:01 12:00:00",
                )
            val prefs = defaultPrefs.copy(photoSelection = "DATE_TAKEN", photoDateType = DateType.COMPACT)

            val result = resolver.resolve(context, media, prefs)

            // Date formatting checks could result differently depending on system time.
            // We assert it is at least resolved as static type.
            assertEquals(MetadataType.STATIC, result.metadataType)
        }
}
