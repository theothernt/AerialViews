package com.neilturner.aerialviews.testconfig

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chibatching.kotpref.Kotpref
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The general settings the test harness applies.
 *
 * `slideshowSpeed` was present in the local SMB config but not in [GeneralConfig], so the parser
 * silently ignored it and the run used the default 30s per photo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TestConfigApplierTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Kotpref.init(context)
    }

    @Test
    fun `applies the slideshow speed from a general config`() {
        val config =
            TestConfig.parse(
                """
                {
                  "name": "slideshow",
                  "general": {
                    "playlistCache": false,
                    "startScreensaverOnLaunch": false,
                    "slideshowSpeed": "3"
                  }
                }
                """.trimIndent(),
            )

        runBlocking { TestConfigApplier.apply(context, config) }

        assertEquals("3", GeneralPrefs.slideshowSpeed)
        assertEquals(false, GeneralPrefs.playlistCache)
    }
}
