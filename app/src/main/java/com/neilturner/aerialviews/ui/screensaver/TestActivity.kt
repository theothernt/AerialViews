package com.neilturner.aerialviews.ui.screensaver

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.core.ScreenController
import com.neilturner.aerialviews.ui.helpers.DeviceHelper
import com.neilturner.aerialviews.ui.helpers.InputHelper
import com.neilturner.aerialviews.ui.helpers.LocaleHelper
import com.neilturner.aerialviews.ui.helpers.PreferenceHelper
import com.neilturner.aerialviews.ui.helpers.WindowHelper.hideSystemUI
import com.neilturner.aerialviews.utils.FirebaseHelper
import timber.log.Timber

class TestActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_FROM_DREAM = "from_dream"
        const val EXTRA_WAKE_SCREEN = "wake_screen"
        private const val DREAM_HANDOVER_GRACE_MS = 10_000L
    }

    private lateinit var screenController: ScreenController
    private var startedAt = 0L
    private var relaunchedToWake = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Setup
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setTitle(R.string.app_name)
        supportActionBar?.hide()
        if (intent.getBooleanExtra(EXTRA_WAKE_SCREEN, false)) {
            startedAt = SystemClock.elapsedRealtime()
            relaunchedToWake = true
            turnScreenOn()
        }
        if (intent.getBooleanExtra(EXTRA_FROM_DREAM, false)) {
            startedAt = SystemClock.elapsedRealtime()
            turnScreenOn()
            DreamActivity.dismissHandoffDream()
        }
    }

    // Letting the dream go drops the device out of the dreaming state, which is what makes some TVs
    // swap in their own burn-in screensaver. The screen goes off with it, because the idle timeout
    // has already expired and this window cannot hold the screen until it is visible - so onStop
    // brings this activity back with FLAG_TURN_SCREEN_ON, which switches the panel back on.
    private fun turnScreenOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window?.addFlags(
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
        )
    }

    private fun relaunchToTurnScreenOn() {
        relaunchedToWake = true
        Timber.i("Screen went off handing over from the dream, waking it back up")
        startActivity(
            Intent(this, TestActivity::class.java).apply {
                putExtra(EXTRA_WAKE_SCREEN, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
        )
    }

    // Started from the dream, this activity spends its first moments behind the dream window and
    // is stopped - without this it would finish itself before the dream hands the screen over
    private fun isWaitingForDreamToDismiss(): Boolean =
        startedAt != 0L && SystemClock.elapsedRealtime() - startedAt < DREAM_HANDOVER_GRACE_MS

    override fun onResume() {
        super.onResume()
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        FirebaseHelper.analyticsScreenView("Test Screensaver", this)
    }

    override fun onPause() {
        super.onPause()
        window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Using OSD TV menus calls suspend but the screensaver is still running
        // So only stop if on phone or tablet
        if (!DeviceHelper.isTV(this) && this::screenController.isInitialized) {
            screenController.stop()
            finishWithResult()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::screenController.isInitialized) {
            screenController.stop()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        // Hide system UI on phones
        hideSystemUI(window)

        // Start playback, etc
        screenController =
            if (GeneralPrefs.localeScreensaver.startsWith("default")) {
                ScreenController(this)
            } else {
                val altContext = LocaleHelper.alternateLocale(this, GeneralPrefs.localeScreensaver)
                ScreenController(altContext)
            }
        setContentView(screenController.view)

        InputHelper.setupGestureListener(
            context = this,
            controller = screenController,
            exit = ::finishWithResult,
        )
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (this::screenController.isInitialized &&
            InputHelper.handleKeyEvent(event, screenController, ::finishWithResult)
        ) {
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (InputHelper.handleGenericMotionEvent(event, ::finishWithResult)) {
            return true
        }

        return super.dispatchGenericMotionEvent(event)
    }

    override fun onStop() {
        super.onStop()
        // Stop playback, animations, etc
        // Stop here in TV, already stopped in onPause if phone
        if (isWaitingForDreamToDismiss()) {
            if (!relaunchedToWake) {
                relaunchToTurnScreenOn()
            }
            return
        }
        window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (this::screenController.isInitialized && DeviceHelper.isTV(this)) {
            screenController.stop()
            finishAndRemoveTask()
        }
    }

    private fun finishWithResult(exitApp: Boolean = false) {
        Timber.i(
            "isExitToSettingSet: ${PreferenceHelper.isExitToSettingSet()}, exitApp: $exitApp, startScreensaverOnLaunch: ${GeneralPrefs.startScreensaverOnLaunch}",
        )

        val shouldExitApp = (
            GeneralPrefs.startScreensaverOnLaunch &&
                exitApp &&
                PreferenceHelper.isExitToSettingSet()
        )
        val resultIntent =
            Intent().apply {
                putExtra("exit_app", shouldExitApp)
            }
        setResult(RESULT_OK, resultIntent)
        // If a dream handed playback over, it is still running underneath holding the screen on
        DreamActivity.dismissHandoffDream()
        finish()
    }
}
