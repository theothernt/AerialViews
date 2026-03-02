package com.neilturner.aerialviews.providers.immich

import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.prefs.ImmichMediaPrefs
import com.neilturner.aerialviews.models.videos.AerialExifMetadata
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.AerialMediaMetadata
import com.neilturner.aerialviews.utils.FileHelper
import timber.log.Timber

class ImmichAssetMapper(
    private val prefs: ImmichMediaPrefs,
    private val urlBuilder: ImmichUrlBuilder
) {
    data class ProcessResults(
        val media: List<AerialMedia>,
        val excluded: Int,
        val videos: Int,
        val images: Int,
    )

    fun filterAssetsByMediaType(assets: List<Asset>): List<Asset> =
        assets.filter { asset ->
            val filename = asset.originalPath
            when {
                FileHelper.isSupportedVideoType(filename) -> prefs.mediaType != ProviderMediaType.PHOTOS
                FileHelper.isSupportedImageType(filename) -> prefs.mediaType != ProviderMediaType.VIDEOS
                else -> false // Exclude unsupported files
            }
        }

    fun processAssets(assets: List<Asset>): ProcessResults {
        val media = mutableListOf<AerialMedia>()
        var excluded = 0
        var videos = 0
        var images = 0

        assets.forEach { asset ->
            val exif = extractExifMetadata(asset)
            val filename = asset.originalPath
            val rawExif = asset.exifInfo

            Timber.i(
                "Immich EXIF: path=%s localDateTime=%s description=%s city=%s state=%s country=%s",
                filename,
                asset.localDateTime,
                asset.description ?: rawExif?.description,
                rawExif?.city,
                rawExif?.state,
                rawExif?.country,
            )

            val isVideo = FileHelper.isSupportedVideoType(filename)
            val isImage = FileHelper.isSupportedImageType(filename)

            if (isVideo || isImage) {
                val uri = urlBuilder.getAssetUri(asset.id, isVideo)
                val item =
                    AerialMedia(
                        uri,
                        metadata =
                            AerialMediaMetadata(
                                exif = exif,
                            ),
                    ).apply {
                        source = AerialMediaSource.IMMICH
                        type = if (isVideo) AerialMediaType.VIDEO else AerialMediaType.IMAGE
                    }

                if (isVideo) {
                    videos++
                    if (prefs.mediaType != ProviderMediaType.PHOTOS) {
                        media.add(item)
                    }
                } else {
                    images++
                    if (prefs.mediaType != ProviderMediaType.VIDEOS) {
                        media.add(item)
                    }
                }
            } else {
                excluded++
            }
        }

        return ProcessResults(
            media = media,
            excluded = excluded,
            videos = videos,
            images = images,
        )
    }

    private fun extractExifMetadata(asset: Asset): AerialExifMetadata {
        val exifInfo = asset.exifInfo
        return AerialExifMetadata(
            date = asset.localDateTime?.let(::normalizeImmichLocalDateTime),
            offset = null,
            city = exifInfo?.city,
            state = exifInfo?.state,
            country = exifInfo?.country,
            description = asset.description ?: exifInfo?.description,
        )
    }

    private fun normalizeImmichLocalDateTime(value: String): String {
        val normalized = value.trim()
        return normalized.replace(Regex("(?i)(?:z|[+-]\\d{2}:?\\d{2})$"), "")
    }
}
