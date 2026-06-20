package com.neilturner.aerialviews.di

import com.neilturner.aerialviews.ui.core.MetadataResolver
import com.neilturner.aerialviews.ui.core.ScreenController
import com.neilturner.aerialviews.ui.core.ScreensaverViewModel
import com.neilturner.aerialviews.ui.overlays.state.OverlayStateStore
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val screensaverModule =
    module {
        singleOf(::OverlayStateStore)
        factoryOf(::MetadataResolver)
        factoryOf(::ScreenController)
        viewModelOf(::ScreensaverViewModel)
    }
