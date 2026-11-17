package com.neilturner.aerialviews.providers

import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.AerialMediaSource

internal class AmazonMediaProviderTest :
    BundledVideosProviderContract(
        videosResId = R.raw.fireos8,
        stringsResId = R.raw.fireos8_strings,
        expectedSource = AerialMediaSource.AMAZON,
        dayFilenameFor1080Sdr = "day_sdr",
        createProvider = { context, prefs -> AmazonMediaProvider(context, prefs) },
    )
