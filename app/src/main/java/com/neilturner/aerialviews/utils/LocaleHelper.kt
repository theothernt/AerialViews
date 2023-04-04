package com.neilturner.aerialviews.utils

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.RawRes
import java.io.InputStream
import java.util.Locale

object LocaleHelper {

    // https://stackoverflow.com/a/44145807/247257
    fun localeString(
        requestedLocale: Locale?,
        resourceId: Int,
        context: Context
    ): String {
        val config = Configuration(context.resources.configuration)
        config.setLocale(requestedLocale)
        return context.createConfigurationContext(config).getText(resourceId).toString()
    }

    fun localeResource(
        requestedLocale: Locale?,
        @RawRes res: Int,
        context: Context
    ): InputStream {
        val config = Configuration(context.resources.configuration)
        config.setLocale(requestedLocale)
        return context.createConfigurationContext(config).resources.openRawResource(res)
    }
}
