package com.neilturner.aerialviews.providers

import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.AerialMediaSource

internal class Comm2MediaProviderTest :
    BundledVideosProviderContract(
        videosResId = R.raw.comm2,
        stringsResId = R.raw.comm2_strings,
        expectedSource = AerialMediaSource.COMM2,
        dayFilenameFor1080Sdr = "day_hdr",
        createProvider = { context, prefs -> Comm2MediaProvider(context, prefs) },
    )
