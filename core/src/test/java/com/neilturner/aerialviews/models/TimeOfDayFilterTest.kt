package com.neilturner.aerialviews.models

import android.net.Uri
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.TimeOfDay
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.AerialMediaMetadata
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class TimeOfDayFilterTest {
    @Test
    fun `day filter includes day unknown and optionally sunrise`() {
        val filter = TimeOfDayFilter.forPeriod(TimeOfDay.DAY, dayIncludes = setOf("SUNRISE"), nightIncludes = emptySet())

        assertTrue(filter.enabled)
        assertEquals(
            setOf(TimeOfDay.UNKNOWN, TimeOfDay.DAY, TimeOfDay.SUNRISE),
            filter.allowed,
        )
    }

    @Test
    fun `day filter can include sunset too`() {
        val filter = TimeOfDayFilter.forPeriod(TimeOfDay.DAY, dayIncludes = setOf("SUNRISE", "SUNSET"), nightIncludes = emptySet())

        assertEquals(
            setOf(TimeOfDay.UNKNOWN, TimeOfDay.DAY, TimeOfDay.SUNRISE, TimeOfDay.SUNSET),
            filter.allowed,
        )
    }

    @Test
    fun `night filter includes night unknown and optionally sunset`() {
        val filter = TimeOfDayFilter.forPeriod(TimeOfDay.NIGHT, dayIncludes = emptySet(), nightIncludes = setOf("SUNSET"))

        assertEquals(
            setOf(TimeOfDay.UNKNOWN, TimeOfDay.NIGHT, TimeOfDay.SUNSET),
            filter.allowed,
        )
    }

    @Test
    fun `photos always pass the filter regardless of their metadata`() {
        val filter = TimeOfDayFilter.forPeriod(TimeOfDay.DAY, dayIncludes = emptySet(), nightIncludes = emptySet())
        val nightPhoto = media(AerialMediaType.IMAGE, TimeOfDay.NIGHT)
        val sunsetPhoto = media(AerialMediaType.IMAGE, TimeOfDay.SUNSET)
        val unknownPhoto = media(AerialMediaType.IMAGE, TimeOfDay.UNKNOWN)

        assertTrue(filter.includes(nightPhoto))
        assertTrue(filter.includes(sunsetPhoto))
        assertTrue(filter.includes(unknownPhoto))
    }

    @Test
    fun `unknown videos are always kept`() {
        val day = TimeOfDayFilter.forPeriod(TimeOfDay.DAY, emptySet(), emptySet())
        val night = TimeOfDayFilter.forPeriod(TimeOfDay.NIGHT, emptySet(), emptySet())
        val unknown = media(AerialMediaType.VIDEO, TimeOfDay.UNKNOWN)

        assertTrue(day.includes(unknown))
        assertTrue(night.includes(unknown))
    }

    @Test
    fun `apply keeps matching videos and every photo`() {
        val filter = TimeOfDayFilter.forPeriod(TimeOfDay.DAY, dayIncludes = setOf("SUNRISE"), nightIncludes = emptySet())
        val sunrise = media(AerialMediaType.VIDEO, TimeOfDay.SUNRISE)
        val day = media(AerialMediaType.VIDEO, TimeOfDay.DAY)
        val night = media(AerialMediaType.VIDEO, TimeOfDay.NIGHT)
        val photo = media(AerialMediaType.IMAGE, TimeOfDay.NIGHT)

        val result = filter.apply(listOf(sunrise, day, night, photo))

        assertEquals(listOf(sunrise, day, photo), result)
    }

    @Test
    fun `apply falls back to the original list when no videos match`() {
        val filter = TimeOfDayFilter.forPeriod(TimeOfDay.DAY, dayIncludes = emptySet(), nightIncludes = emptySet())
        val nightOne = media(AerialMediaType.VIDEO, TimeOfDay.NIGHT)
        val nightTwo = media(AerialMediaType.VIDEO, TimeOfDay.SUNSET)
        val photo = media(AerialMediaType.IMAGE, TimeOfDay.NIGHT)
        val all = listOf(nightOne, nightTwo, photo)

        val result = filter.apply(all)

        assertSame(all, result)
    }

    @Test
    fun `apply falls back when the list has no videos at all`() {
        val filter = TimeOfDayFilter.forPeriod(TimeOfDay.DAY, dayIncludes = emptySet(), nightIncludes = emptySet())
        val photos = listOf(media(AerialMediaType.IMAGE, TimeOfDay.NIGHT), media(AerialMediaType.IMAGE, TimeOfDay.DAY))

        assertSame(photos, filter.apply(photos))
    }

    @Test
    fun `disabled filter passes everything through`() {
        val all = listOf(media(AerialMediaType.VIDEO, TimeOfDay.NIGHT), media(AerialMediaType.IMAGE, TimeOfDay.DAY))

        assertFalse(TimeOfDayFilter.ALL.enabled)
        assertSame(all, TimeOfDayFilter.ALL.apply(all))
        assertTrue(TimeOfDayFilter.ALL.includes(all.first()))
    }

    @Test
    fun `allowed names match the enum names used by the SQL predicate`() {
        val filter = TimeOfDayFilter.forPeriod(TimeOfDay.NIGHT, dayIncludes = setOf("SUNRISE"), nightIncludes = emptySet())

        assertEquals(setOf("UNKNOWN", "NIGHT"), filter.allowedNames().toSet())
    }

    private fun media(
        type: AerialMediaType,
        timeOfDay: TimeOfDay,
    ) = AerialMedia(
        uri = mockk<Uri>(relaxed = true),
        type = type,
        metadata = AerialMediaMetadata(timeOfDay = timeOfDay),
    )
}
