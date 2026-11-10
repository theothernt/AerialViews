package com.neilturner.aerialviews.ui.screensaver

import com.neilturner.aerialviews.core.AppConfig
import com.neilturner.aerialviews.core.telemetry.Telemetry
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.core.ScreensaverHost
import com.neilturner.aerialviews.ui.core.ScreensaverUi
import timber.log.Timber

/** The implementation selected for a screensaver session. */
enum class ScreensaverUiPath(
    val key: String,
) {
    LEGACY("legacy"),
    COMPOSE("compose"),
    ;

    companion object {
        fun parse(raw: String?): ScreensaverUiPath? = entries.firstOrNull { it.key.equals(raw, ignoreCase = true) }
    }
}

/**
 * Supplies the Compose renderer without letting `:app` or `:core` depend on the Compose module.
 *
 * The callback is invoked when the Compose session has successfully displayed its first item. The
 * factory uses it to reset the crash-loop counter; a renderer must not call it for a failed or empty
 * session.
 */
fun interface ComposeScreensaverUiFactory {
    fun create(onFirstItemDisplayed: () -> Unit): ScreensaverUi
}

/**
 * Owns the single active screensaver UI and selects the renderer for a host.
 *
 * Legacy remains the default. A Compose request falls back to legacy if the renderer is unavailable
 * or throws during construction/attachment. The failure counter is persisted so repeated startup
 * failures disable the opt-in instead of crashing the app on every dream start.
 */
object ScreensaverUiFactory {
    private const val MAX_COMPOSE_FAILURES = 3

    private var composeUiFactory: ComposeScreensaverUiFactory? = null
    private var activeUi: ScreensaverUi? = null
    private var activeHost: ScreensaverHost? = null
    private var activePath: ScreensaverUiPath? = null

    /** Phase 7 registers the Compose renderer here once its module exists. */
    fun registerComposeUiFactory(factory: ComposeScreensaverUiFactory?) {
        composeUiFactory = factory
    }

    /**
     * Creates and attaches the selected UI. Exactly one UI may be active; hosts must call [release]
     * before creating another one.
     */
    fun create(
        host: ScreensaverHost,
        requestedPath: ScreensaverUiPath? = null,
    ): ScreensaverUi {
        check(activeUi == null) {
            "A screensaver UI is already active; release it before creating another one"
        }

        resetFailureCountOnUpgrade()
        val explicitOverride = requestedPath != null
        val requested = requestedPath ?: preferredPath()
        Telemetry.logKey(UI_PATH_KEY, requested.key, alwaysLog = true)

        return if (requested == ScreensaverUiPath.COMPOSE) {
            createComposeOrFallback(host, applyGuard = !explicitOverride)
        } else {
            activate(LegacyScreensaverUi(), ScreensaverUiPath.LEGACY, host)
        }
    }

    /**
     * Releases [ui]. Safe to call more than once and safe to call for a UI that failed to attach.
     * Releasing the activity host also clears the handoff-dream reference that activity owns.
     */
    fun release(ui: ScreensaverUi) {
        val wasActive = activeUi === ui
        val host = if (wasActive) activeHost else null
        if (wasActive) {
            activeUi = null
            activeHost = null
            activePath = null
        }

        try {
            ui.release()
        } catch (e: Exception) {
            Timber.e(e, "Screensaver UI release failed")
            Telemetry.recordException(e, alwaysLog = true)
        } finally {
            if (wasActive && host is ActivityScreensaverHost) {
                DreamActivity.dismissHandoffDream()
            }
        }
    }

    /** Called when the user explicitly re-enables Compose after the automatic guard tripped. */
    fun resetComposeFailureCount() {
        GeneralPrefs.composeScreensaverFailureCount = 0
        GeneralPrefs.composeScreensaverFailureVersionCode = AppConfig.versionCode
    }

    private fun createComposeOrFallback(
        host: ScreensaverHost,
        applyGuard: Boolean,
    ): ScreensaverUi {
        if (applyGuard && !beginComposeAttempt()) {
            return activate(LegacyScreensaverUi(), ScreensaverUiPath.LEGACY, host)
        }

        return try {
            val factory =
                composeUiFactory
                    ?: error("Compose screensaver UI is not installed")
            val ui =
                factory.create(
                    if (applyGuard) {
                        ::onComposeFirstItemDisplayed
                    } else {
                        {}
                    },
                )
            activate(ui, ScreensaverUiPath.COMPOSE, host)
        } catch (e: Exception) {
            Timber.e(e, "Compose screensaver failed to start; falling back to legacy")
            Telemetry.recordException(e, alwaysLog = true)
            Telemetry.logMessage(
                "Compose screensaver fell back to legacy",
                alwaysLog = true,
            )
            if (applyGuard) {
                disableComposeIfFailureLimitReached()
            }
            activate(LegacyScreensaverUi(), ScreensaverUiPath.LEGACY, host)
        }
    }

    private fun activate(
        ui: ScreensaverUi,
        path: ScreensaverUiPath,
        host: ScreensaverHost,
    ): ScreensaverUi {
        activeUi = ui
        activeHost = host
        activePath = path
        return try {
            ui.attach(host)
            Telemetry.logKey(UI_PATH_KEY, path.key, alwaysLog = true)
            ui
        } catch (e: Exception) {
            activeUi = null
            activeHost = null
            activePath = null
            runCatching { ui.release() }
                .onFailure { releaseError ->
                    Timber.e(releaseError, "Failed to release a screensaver UI that failed to attach")
                }
            throw e
        }
    }

    private fun beginComposeAttempt(): Boolean {
        if (GeneralPrefs.composeScreensaverFailureCount >= MAX_COMPOSE_FAILURES) {
            disableComposeIfFailureLimitReached()
            return false
        }

        GeneralPrefs.composeScreensaverFailureCount += 1
        GeneralPrefs.composeScreensaverFailureVersionCode = AppConfig.versionCode
        return true
    }

    private fun disableComposeIfFailureLimitReached() {
        if (GeneralPrefs.composeScreensaverFailureCount >= MAX_COMPOSE_FAILURES &&
            GeneralPrefs.useComposeScreensaver
        ) {
            GeneralPrefs.useComposeScreensaver = false
            Timber.w("Compose screensaver disabled after repeated startup failures")
            Telemetry.logMessage(
                "Compose screensaver disabled after $MAX_COMPOSE_FAILURES consecutive failures",
                alwaysLog = true,
            )
        }
    }

    private fun onComposeFirstItemDisplayed() {
        if (activePath == ScreensaverUiPath.COMPOSE) {
            resetComposeFailureCount()
        }
    }

    private fun resetFailureCountOnUpgrade() {
        if (GeneralPrefs.composeScreensaverFailureVersionCode != AppConfig.versionCode) {
            GeneralPrefs.composeScreensaverFailureCount = 0
            GeneralPrefs.composeScreensaverFailureVersionCode = AppConfig.versionCode
        }
    }

    private fun preferredPath(): ScreensaverUiPath =
        if (GeneralPrefs.useComposeScreensaver) {
            ScreensaverUiPath.COMPOSE
        } else {
            ScreensaverUiPath.LEGACY
        }

    private const val UI_PATH_KEY = "ui_path"
}
