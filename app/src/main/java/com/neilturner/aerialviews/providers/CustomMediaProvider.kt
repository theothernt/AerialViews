package com.neilturner.aerialviews.providers

import android.content.Context
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.prefs.CustomMediaPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia

class CustomMediaProvider(
    context: Context,
    private val prefs: CustomMediaPrefs,
) : MediaProvider(context) {
    override val type = ProviderSourceType.REMOTE

    override val enabled: Boolean
        get() = prefs.enabled

    override suspend fun fetchMedia(): List<AerialMedia> {
        TODO("Not yet implemented")
    }

    override suspend fun fetchTest(): String {
        TODO("Not yet implemented")
    }

    override suspend fun fetchMetadata(): MutableMap<String, Pair<String, Map<Int, String>>> {
        TODO("Not yet implemented")
    }

}
