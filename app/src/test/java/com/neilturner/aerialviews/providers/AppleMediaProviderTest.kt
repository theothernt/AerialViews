package com.neilturner.aerialviews.providers

import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.AerialMediaSource

internal class AppleMediaProviderTest :
    BundledVideosProviderContract(
        videosResId = R.raw.tvos26,
        stringsResId = R.raw.tvos26_strings,
        expectedSource = AerialMediaSource.APPLE,
        dayFilenameFor1080Sdr = "day_sdr",
        createProvider = { context, prefs -> AppleMediaProvider(context, prefs) },
    )
