package com.neilturner.aerialviews.ui.core

import android.content.Context
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.DateType
import com.neilturner.aerialviews.models.enums.LocationType
import com.neilturner.aerialviews.models.enums.MetadataType
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.utils.DateHelper
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.GeocoderHelper
import com.neilturner.aerialviews.utils.filenameWithoutExtension
import java.util.Locale

internal class MetadataResolver(
    private val geocoderHelper: GeocoderHelper = GeocoderHelper,
) {
    data class ResolvedMetadata(
        val text: String,
        val poi: Map<Int, String>,
        val metadataType: MetadataType,
    )

    data class Preferences(
        val videoSelection: String,
        val videoFolderDepth: Int,
        val photoSelection: String,
        val photoFolderDepth: Int,
        val photoLocationType: LocationType,
        val photoDateType: DateType,
        val photoDateCustom: String,
    )

    suspend fun resolve(
        context: Context,
        media: AerialMedia,
        preferences: Preferences,
    ): ResolvedMetadata =
        if (media.type == AerialMediaType.IMAGE) {
            resolvePhoto(context, media, preferences)
        } else {
            resolveVideo(media, preferences)
        }

    private fun resolveVideo(media: AerialMedia, preferences: Preferences): ResolvedMetadata {
        val selection = parseSelection(preferences.videoSelection)

        for (entry in selection) {
            when (entry) {
                "POI" -> {
                    if (hasUsablePoi(media)) {
                        return ResolvedMetadata(
                            text = media.metadata.shortDescription,
                            poi = media.metadata.pointsOfInterest,
                            metadataType = MetadataType.DYNAMIC,
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
                                metadataType = MetadataType.STATIC,
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
                                metadataType = MetadataType.STATIC,
                            )
                        }
                }

                "FOLDER_FILENAME" -> {
                    FileHelper
                        .formatFolderAndFilenameFromUri(media.uri, includeFilename = true, pathDepth = preferences.videoFolderDepth)
                        .trim()
                        .takeIf { it.isNotBlank() }
                        ?.let {
                            return ResolvedMetadata(
                                text = it,
                                poi = emptyMap(),
                                metadataType = MetadataType.STATIC,
                            )
                        }
                }

                "FOLDER_ONLY" -> {
                    FileHelper
                        .formatFolderAndFilenameFromUri(media.uri, includeFilename = false, pathDepth = preferences.videoFolderDepth)
                        .trim()
                        .takeIf { it.isNotBlank() }
                        ?.let {
                            return ResolvedMetadata(
                                text = it,
                                poi = emptyMap(),
                                metadataType = MetadataType.STATIC,
                            )
                        }
                }
            }
        }

        return ResolvedMetadata(
            text = "",
            poi = emptyMap(),
            metadataType = MetadataType.STATIC,
        )
    }

    private suspend fun resolvePhoto(
        context: Context,
        media: AerialMedia,
        preferences: Preferences,
    ): ResolvedMetadata {
        val selection = parseSelection(preferences.photoSelection)

        for (entry in selection) {
            when (entry) {
                "LOCATION" -> {
                    when (val location = resolvePhotoLocation(context, media, preferences.photoLocationType)) {
                        is PhotoLocationResolution.Resolved -> {
                            return ResolvedMetadata(
                                text = location.text,
                                poi = emptyMap(),
                                metadataType = MetadataType.STATIC,
                            )
                        }

                        PhotoLocationResolution.ContinueFallback -> {
                            continue
                        }

                        PhotoLocationResolution.StopWithBlank -> {
                            return ResolvedMetadata(
                                text = "",
                                poi = emptyMap(),
                                metadataType = MetadataType.STATIC,
                            )
                        }
                    }
                }

                "DATE_TAKEN" -> {
                    val exifDate = media.metadata.exif.date
                    if (!exifDate.isNullOrBlank()) {
                        val formatted =
                            DateHelper.formatExifDate(
                                date = exifDate,
                                offset = media.metadata.exif.offset,
                                type = preferences.photoDateType,
                                custom = preferences.photoDateCustom,
                            )
                        if (!formatted.isNullOrBlank()) {
                            return ResolvedMetadata(
                                text = formatted,
                                poi = emptyMap(),
                                metadataType = MetadataType.STATIC,
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
                                metadataType = MetadataType.STATIC,
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
                                metadataType = MetadataType.STATIC,
                            )
                        }
                }

                "FOLDER_FILENAME" -> {
                    FileHelper
                        .formatFolderAndFilenameFromUri(media.uri, includeFilename = true, pathDepth = preferences.photoFolderDepth)
                        .trim()
                        .takeIf { it.isNotBlank() }
                        ?.let {
                            return ResolvedMetadata(
                                text = it,
                                poi = emptyMap(),
                                metadataType = MetadataType.STATIC,
                            )
                        }
                }

                "FOLDER_ONLY" -> {
                    FileHelper
                        .formatFolderAndFilenameFromUri(media.uri, includeFilename = false, pathDepth = preferences.photoFolderDepth)
                        .trim()
                        .takeIf { it.isNotBlank() }
                        ?.let {
                            return ResolvedMetadata(
                                text = it,
                                poi = emptyMap(),
                                metadataType = MetadataType.STATIC,
                            )
                        }
                }
            }
        }

        return ResolvedMetadata(
            text = "",
            poi = emptyMap(),
            metadataType = MetadataType.STATIC,
        )
    }

    private suspend fun resolvePhotoLocation(
        context: Context,
        media: AerialMedia,
        locationType: LocationType,
    ): PhotoLocationResolution {
        val modelLocation =
            GeocoderHelper.GeocodedLocation(
                city = media.metadata.exif.city,
                state = media.metadata.exif.state,
                country = media.metadata.exif.country,
            )

        val fromModel = formatLocation(modelLocation, locationType)
        if (!fromModel.isNullOrBlank()) {
            return PhotoLocationResolution.Resolved(fromModel)
        }

        val latitude = media.metadata.exif.latitude
        val longitude = media.metadata.exif.longitude
        if (latitude == null || longitude == null) {
            return PhotoLocationResolution.ContinueFallback
        }

        val location = geocoderHelper.reverseGeocode(context, latitude, longitude) ?: return PhotoLocationResolution.StopWithBlank
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
        media.metadata.pointsOfInterest.values.any { it.isNotBlank() }

    private fun parseSelection(value: String): List<String> =
        value
            .split(",")
            .map { it.trim().uppercase(Locale.ROOT) }
            .filter { it.isNotBlank() }

    private sealed class PhotoLocationResolution {
        data object ContinueFallback : PhotoLocationResolution()

        data object StopWithBlank : PhotoLocationResolution()

        data class Resolved(
            val text: String,
        ) : PhotoLocationResolution()
    }
}
