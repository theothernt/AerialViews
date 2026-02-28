package com.neilturner.aerialviews.providers.immich

import android.net.Uri
import androidx.core.net.toUri
import com.neilturner.aerialviews.models.enums.ImmichAuthType
import com.neilturner.aerialviews.models.enums.ImmichImageType
import com.neilturner.aerialviews.models.enums.ImmichVideoType
import com.neilturner.aerialviews.models.prefs.ImmichMediaPrefs

class ImmichUrlBuilder(
    private val server: String,
    private val prefs: ImmichMediaPrefs,
    private var resolvedSharedKey: String? = null
) {
    fun setResolvedSharedKey(key: String?) {
        resolvedSharedKey = key
    }

    private fun cleanSharedLinkKey(input: String): String {
        return input
            .trim()
            .replace(Regex("^/+|+/$"), "") // Remove leading and trailing slashes
            .replace(Regex("^(share|s)/"), "") // Support both "/share/<key>" and "/s/<slug>" formats
    }

    fun getAssetUri(
        id: String,
        isVideo: Boolean,
    ): Uri {
        val cleanedKey = resolvedSharedKey ?: cleanSharedLinkKey(prefs.pathName)
        return when (prefs.authType) {
            ImmichAuthType.SHARED_LINK -> {
                val base =
                    if (isVideo) {
                        if (prefs.videoType == ImmichVideoType.TRANSCODED) {
                            "$server/api/assets/$id/video/playback?key=$cleanedKey"
                        } else {
                            "$server/api/assets/$id/original?key=$cleanedKey"
                        }
                    } else {
                        if (prefs.imageType == ImmichImageType.ORIGINAL) {
                            "$server/api/assets/$id/original?key=$cleanedKey"
                        } else {
                            val size = if (prefs.imageType == ImmichImageType.FULLSIZE) "fullsize" else "preview"
                            "$server/api/assets/$id/thumbnail?size=$size&key=$cleanedKey"
                        }
                    }
                val url = if (prefs.password.isNotEmpty()) "$base&password=${prefs.password}" else base
                url.toUri()
            }

            // "fullsize" will use fullsize or reencoded pic as configured within Immich
            // "preview" will use preview-reencoded pic as configured within Immich, 1440p by default
            ImmichAuthType.API_KEY -> {
                if (isVideo) {
                    if (prefs.videoType == ImmichVideoType.TRANSCODED) {
                        "$server/api/assets/$id/video/playback".toUri()
                    } else {
                        "$server/api/assets/$id/original".toUri()
                    }
                } else {
                    if (prefs.imageType == ImmichImageType.ORIGINAL) {
                        "$server/api/assets/$id/original".toUri()
                    } else {
                        val size = if (prefs.imageType == ImmichImageType.FULLSIZE) "fullsize" else "preview"
                        "$server/api/assets/$id/thumbnail?size=$size".toUri()
                    }
                }
            }

            null -> {
                throw IllegalStateException("Invalid authentication type")
            }
        }
    }
}
