package com.neilturner.aerialviews.ui.core

import android.content.Context
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.DescriptionManifestType
import com.neilturner.aerialviews.models.enums.DateType
import com.neilturner.aerialviews.models.enums.LocationType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.utils.DateHelper
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.GeocoderHelper
import com.neilturner.aerialviews.utils.filenameWithoutExtension
import java.util.Locale

internal class MetadataSlot1Resolver(
    private val geocoderHelper: GeocoderHelper = GeocoderHelper,
) {
    data class ResolvedMetadata(
        val text: String,
        val poi: Map<Int, String>,
        val descriptionManifestType: DescriptionManifestType,
    )

    suspend fun resolve(
        context: Context,
        media: AerialMedia,
    ): ResolvedMetadata =
        if (media.type == AerialMediaType.IMAGE) {
            resolvePhoto(context, media)
        } else {
            resolveVideo(media)
        }

    private fun resolveVideo(media: AerialMedia): ResolvedMetadata {
        val preferences = parseSelection(GeneralPrefs.overlayMetadata1Videos)
        val videoFolderDepth = parseFolderDepth(GeneralPrefs.overlayMetadata1VideosFolderLevel)

        for (entry in preferences) {
            when (entry) {
                "POI" -> {
                    if (hasUsablePoi(media)) {
                        return ResolvedMetadata(
                            text = media.metadata.shortDescription,
                            poi = media.metadata.pointsOfInterest,
                            descriptionManifestType = DescriptionManifestType.POI,
                        )
                    }
                }

                "DESC" -> {
                    media.metadata.shortDescription
                        .trim()
                        .takeIf { it.isNotBlank() }
                        ?.let {
                            return ResolvedMetadata(
                                text = it,
                                poi = emptyMap(),
                                descriptionManifestType = DescriptionManifestType.TITLE,
                            )
                        }
                }

                "FILENAME" -> {
                    media.uri.filenameWithoutExtension
                        .trim()
                        .takeIf { it.isNotBlank() }
                        ?.let {
                            return ResolvedMetadata(
                                text = it,
                                poi = emptyMap(),
                                descriptionManifestType = DescriptionManifestType.TITLE,
                            )
                        }
                }

                "FOLDER_FILENAME" -> {
                    FileHelper
                        .formatFolderAndFilenameFromUri(media.uri, includeFilename = true, pathDepth = videoFolderDepth)
                        .trim()
                        .takeIf { it.isNotBlank() }
                        ?.let {
                            return ResolvedMetadata(
                                text = it,
                                poi = emptyMap(),
                                descriptionManifestType = DescriptionManifestType.TITLE,
                            )
                        }
                }

                "FOLDER_ONLY" -> {
                    FileHelper
                        .formatFolderAndFilenameFromUri(media.uri, includeFilename = false, pathDepth = videoFolderDepth)
                        .trim()
                        .takeIf { it.isNotBlank() }
                        ?.let {
                            return ResolvedMetadata(
                                text = it,
                                poi = emptyMap(),
                                descriptionManifestType = DescriptionManifestType.TITLE,
                            )
                        }
                }
            }
        }

        return ResolvedMetadata(
            text = "",
            poi = emptyMap(),
            descriptionManifestType = DescriptionManifestType.TITLE,
        )
    }

    private suspend fun resolvePhoto(
        context: Context,
        media: AerialMedia,
    ): ResolvedMetadata {
        val preferences = parseSelection(GeneralPrefs.overlayMetadata1Photos)
        val photoFolderDepth = parseFolderDepth(GeneralPrefs.overlayMetadata1PhotosFolderLevel)

        for (entry in preferences) {
            when (entry) {
                "LOCATION" -> {
                    when (val location = resolvePhotoLocation(context, media)) {
                        is PhotoLocationResolution.Resolved -> {
                            return ResolvedMetadata(
                                text = location.text,
                                poi = emptyMap(),
                                descriptionManifestType = DescriptionManifestType.TITLE,
                            )
                        }

                        PhotoLocationResolution.ContinueFallback -> {
                            continue
                        }

                        PhotoLocationResolution.StopWithBlank -> {
                            return ResolvedMetadata(
                                text = "",
                                poi = emptyMap(),
                                descriptionManifestType = DescriptionManifestType.TITLE,
                            )
                        }
                    }
                }

                "DATE_TAKEN" -> {
                    val exifDate = media.metadata.exif.date
                    if (!exifDate.isNullOrBlank()) {
                        val dateType = GeneralPrefs.overlayMetadata1PhotosDateType ?: DateType.COMPACT
                        val formatted =
                            DateHelper.formatExifDate(
                                date = exifDate,
                                offset = media.metadata.exif.offset,
                                type = dateType,
                                custom = GeneralPrefs.overlayMetadata1PhotosDateCustom,
                            )
                        if (!formatted.isNullOrBlank()) {
                            return ResolvedMetadata(
                                text = formatted,
                                poi = emptyMap(),
                                descriptionManifestType = DescriptionManifestType.TITLE,
                            )
                        }
                    }
                }

                "DESCRIPTION" -> {
                    media.metadata.exif.description
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let {
                            return ResolvedMetadata(
                                text = it,
                                poi = emptyMap(),
                                descriptionManifestType = DescriptionManifestType.TITLE,
                            )
                        }
                }

                "FILENAME" -> {
                    media.uri.filenameWithoutExtension
                        .trim()
                        .takeIf { it.isNotBlank() }
                        ?.let {
                            return ResolvedMetadata(
                                text = it,
                                poi = emptyMap(),
                                descriptionManifestType = DescriptionManifestType.TITLE,
                            )
                        }
                }

                "FOLDER_FILENAME" -> {
                    FileHelper
                        .formatFolderAndFilenameFromUri(media.uri, includeFilename = true, pathDepth = photoFolderDepth)
                        .trim()
                        .takeIf { it.isNotBlank() }
                        ?.let {
                            return ResolvedMetadata(
                                text = it,
                                poi = emptyMap(),
                                descriptionManifestType = DescriptionManifestType.TITLE,
                            )
                        }
                }

                "FOLDER_ONLY" -> {
                    FileHelper
                        .formatFolderAndFilenameFromUri(media.uri, includeFilename = false, pathDepth = photoFolderDepth)
                        .trim()
                        .takeIf { it.isNotBlank() }
                        ?.let {
                            return ResolvedMetadata(
                                text = it,
                                poi = emptyMap(),
                                descriptionManifestType = DescriptionManifestType.TITLE,
                            )
                        }
                }
            }
        }

        return ResolvedMetadata(
            text = "",
            poi = emptyMap(),
            descriptionManifestType = DescriptionManifestType.TITLE,
        )
    }

    private suspend fun resolvePhotoLocation(
        context: Context,
        media: AerialMedia,
    ): PhotoLocationResolution {
        val latitude = media.metadata.exif.latitude
        val longitude = media.metadata.exif.longitude

        if (latitude == null || longitude == null) {
            return PhotoLocationResolution.ContinueFallback
        }

        val location = geocoderHelper.reverseGeocode(context, latitude, longitude) ?: return PhotoLocationResolution.StopWithBlank
        val locationType = GeneralPrefs.overlayMetadata1PhotosLocationType ?: LocationType.CITY_COUNTRY
        val formatted = formatLocation(location, locationType)

        return if (formatted.isNullOrBlank()) {
            PhotoLocationResolution.StopWithBlank
        } else {
            PhotoLocationResolution.Resolved(formatted)
        }
    }

    private fun formatLocation(
        location: GeocoderHelper.GeocodedLocation,
        locationType: LocationType,
    ): String? {
        val city = location.city?.takeIf { it.isNotBlank() }
        val state = location.state?.takeIf { it.isNotBlank() }
        val country = location.country?.takeIf { it.isNotBlank() }

        return when (locationType) {
            LocationType.CITY -> {
                city ?: country
            }

            LocationType.CITY_STATE -> {
                if (!city.isNullOrBlank() && !state.isNullOrBlank()) {
                    "$city, $state"
                } else {
                    country
                }
            }

            LocationType.CITY_COUNTRY -> {
                if (!city.isNullOrBlank() && !country.isNullOrBlank()) {
                    "$city, $country"
                } else {
                    country
                }
            }

            LocationType.CITY_STATE_COUNTRY -> {
                if (!city.isNullOrBlank() && !state.isNullOrBlank() && !country.isNullOrBlank()) {
                    "$city, $state, $country"
                } else {
                    country
                }
            }

            LocationType.COUNTRY -> {
                country
            }
        }
    }

    private fun hasUsablePoi(media: AerialMedia): Boolean =
        media.metadata.pointsOfInterest.values.any { !it.isNullOrBlank() }

    private fun parseSelection(value: String): List<String> =
        value
            .split(",")
            .map { it.trim().uppercase(Locale.ROOT) }
            .filter { it.isNotBlank() }

    private fun parseFolderDepth(value: String): Int = value.toIntOrNull()?.coerceIn(1, 5) ?: 1

    private sealed class PhotoLocationResolution {
        data object ContinueFallback : PhotoLocationResolution()

        data object StopWithBlank : PhotoLocationResolution()

        data class Resolved(
            val text: String,
        ) : PhotoLocationResolution()
    }
}
