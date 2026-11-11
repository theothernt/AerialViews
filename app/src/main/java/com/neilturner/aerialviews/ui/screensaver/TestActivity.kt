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
import com.neilturner.aerialviews.ui.core.ExitReason
import com.neilturner.aerialviews.ui.core.ScreensaverUi
import com.neilturner.aerialviews.ui.helpers.DeviceHelper
import com.neilturner.aerialviews.ui.helpers.PreferenceHelper
import com.neilturner.aerialviews.ui.helpers.WindowHelper.hideSystemUI
import com.neilturner.aerialviews.utils.FirebaseHelper
import timber.log.Timber

class TestActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_FROM_DREAM = "from_dream"
        const val EXTRA_WAKE_SCREEN = "wake_screen"
        const val EXTRA_UI_PATH = "ui_path"
        private const val DREAM_HANDOVER_GRACE_MS = 10_000L
    }

    private var screensaverUi: ScreensaverUi? = null
    private var activityStarted = false
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
    @Suppress("DEPRECATION")
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

    override fun onStart() {
        super.onStart()
        activityStarted = true
        screensaverUi?.onStart()
    }

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
        if (!DeviceHelper.isTV(this) && screensaverUi != null) {
            screensaverUi?.onStop()
            finishWithResult()
        }
    }

    override fun onDestroy() {
        releaseScreensaverUi()
        super.onDestroy()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        // Hide system UI on phones
        hideSystemUI(window)

        releaseScreensaverUi()

        val host =
            ActivityScreensaverHost(
                context = screensaverLocaleContext(this),
                window = window,
                lifecycleOwner = this,
                savedStateRegistryOwner = this,
                viewModelStoreOwner = this,
                onExit = ::finishWithReason,
            )
        screensaverUi = ScreensaverUiFactory.create(host, requestedUiPath())
        if (activityStarted) {
            screensaverUi?.onStart()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (screensaverUi?.onKeyEvent(event) == true) {
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (screensaverUi?.onGenericMotionEvent(event) == true) {
            return true
        }

        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (screensaverUi?.onTouchEvent(event) == true) {
            return true
        }

        return super.dispatchTouchEvent(event)
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
        if (DeviceHelper.isTV(this) && screensaverUi != null) {
            activityStarted = false
            screensaverUi?.onStop()
            finishAndRemoveTask()
        }
    }

    private fun requestedUiPath(): ScreensaverUiPath? {
        val raw = intent.getStringExtra(EXTRA_UI_PATH) ?: return null
        return ScreensaverUiPath.parse(raw)
            ?: run {
                Timber.w("Ignoring unknown screensaver ui_path '$raw'")
                null
            }
    }

    private fun finishWithReason(reason: ExitReason) {
        finishWithResult(reason == ExitReason.EXIT)
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

    private fun releaseScreensaverUi() {
        val ui = screensaverUi ?: return
        screensaverUi = null
        ScreensaverUiFactory.release(ui)
    }
}
