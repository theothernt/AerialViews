package com.neilturner.aerialviews.providers.immich

import android.net.Uri
import androidx.core.net.toUri
import com.neilturner.aerialviews.models.enums.ImmichAuthType
import com.neilturner.aerialviews.models.enums.ImmichImageType
import com.neilturner.aerialviews.models.enums.ImmichVideoType
import com.neilturner.aerialviews.models.prefs.ImmichUrlPrefs

class ImmichUrlBuilder(
    private val server: String,
    private val prefs: ImmichUrlPrefs,
    private var resolvedSharedKey: String? = null,
    private val uriFactory: (String) -> Uri = { it.toUri() },
) {
    fun setResolvedSharedKey(key: String?) {
        resolvedSharedKey = key
    }

    fun getAssetUri(
        id: String,
        isVideo: Boolean,
    ): Uri {
        val cleanedKey = resolvedSharedKey ?: cleanSharedLinkKey(prefs.pathName)
        val url = when (prefs.authType) {
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
                if (prefs.password.isNotEmpty()) "$base&password=${prefs.password}" else base
            }

            // "fullsize" will use fullsize or reencoded pic as configured within Immich
            // "preview" will use preview-reencoded pic as configured within Immich, 1440p by default
            ImmichAuthType.API_KEY -> {
                if (isVideo) {
                    if (prefs.videoType == ImmichVideoType.TRANSCODED) {
                        "$server/api/assets/$id/video/playback"
                    } else {
                        "$server/api/assets/$id/original"
                    }
                } else {
                    if (prefs.imageType == ImmichImageType.ORIGINAL) {
                        "$server/api/assets/$id/original"
                    } else {
                        val size = if (prefs.imageType == ImmichImageType.FULLSIZE) "fullsize" else "preview"
                        "$server/api/assets/$id/thumbnail?size=$size"
                    }
                }
            }

            null -> {
                throw IllegalStateException("Invalid authentication type")
            }
        }
        return uriFactory(url)
    }
}
