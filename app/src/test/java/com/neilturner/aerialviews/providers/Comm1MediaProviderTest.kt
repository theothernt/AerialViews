package com.neilturner.aerialviews.providers

import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.AerialMediaSource

internal class Comm1MediaProviderTest :
    BundledVideosProviderContract(
        videosResId = R.raw.comm1,
        stringsResId = R.raw.comm1_strings,
        expectedSource = AerialMediaSource.COMM1,
        dayFilenameFor1080Sdr = "day_sdr",
        createProvider = { context, prefs -> Comm1MediaProvider(context, prefs) },
    )
