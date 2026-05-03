package com.neilturner.aerialviews.di

import com.neilturner.aerialviews.data.PlaylistCacheRepository
import com.neilturner.aerialviews.services.MessageRepository
import com.neilturner.aerialviews.services.PlaybackProgressRepository
import com.neilturner.aerialviews.services.KtorServer
import com.neilturner.aerialviews.services.MediaService
import com.neilturner.aerialviews.services.NowPlayingService
import com.neilturner.aerialviews.services.weather.WeatherService
import com.neilturner.aerialviews.ui.core.ScreenViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { PlaylistCacheRepository(androidContext()) }
    single { MediaService(androidContext()) }
    single { NowPlayingService(androidContext()) }
    single { WeatherService(androidContext()) }
    single { MessageRepository() }
    single { PlaybackProgressRepository() }
    
    viewModel { 
        ScreenViewModel(
            context = androidContext(),
            mediaService = get(),
            nowPlayingService = get(),
            weatherService = get(),
            cacheRepository = get(),
            messageRepository = get<MessageRepository>(),
            progressRepository = get<PlaybackProgressRepository>()
        ) 
    }
}
