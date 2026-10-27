package com.neilturner.aerialviews.testconfig

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Process
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * Applies a runtime test configuration to the app preferences.
 *
 * The receiver is disabled in the manifest so that it is inert in normal builds. Enable it on a
 * test device with:
 *
 * ```
 * adb shell pm enable com.neilturner.aerialviews/com.neilturner.aerialviews.testconfig.TestConfigReceiver
 * ```
 *
 * Apply a config with:
 *
 * ```
 * adb shell am broadcast -n com.neilturner.aerialviews/com.neilturner.aerialviews.testconfig.TestConfigReceiver \
 *   -a com.neilturner.aerialviews.testconfig.APPLY \
 *   --es path /sdcard/Android/data/com.neilturner.aerialviews/files/test-config.json
 * ```
 *
 * Pass `--ez partial true` to apply only [TestConfig.general] and leave the providers, media
 * selections and button mappings untouched. Full configs reset every provider they do not name,
 * which is not wanted when a benchmark only needs one preference flipped on a real device.
 */
class TestConfigReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val callingUid = Binder.getCallingUid()
        if (callingUid != Process.SHELL_UID && callingUid != Process.ROOT_UID && callingUid != Process.myUid()) {
            Timber.w("TestConfigReceiver: ignoring request from uid $callingUid")
            return
        }

        val json =
            intent.getStringExtra(EXTRA_JSON)
                ?: intent.getStringExtra(EXTRA_PATH)?.let { path -> readConfig(context, path) }

        if (json.isNullOrBlank()) {
            Timber.e("TestConfigReceiver: no config supplied")
            return
        }

        runCatching {
            val config = TestConfig.parse(json)
            val partial = intent.getBooleanExtra(EXTRA_PARTIAL, false)
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    TestConfigApplier.apply(context, config, partial)
                } catch (ex: Exception) {
                    Timber.e(ex, "TestConfigReceiver: failed to apply config")
                } finally {
                    pendingResult.finish()
                }
            }
        }.onFailure { ex ->
            Timber.e(ex, "TestConfigReceiver: failed to apply config")
        }
    }

    private fun readConfig(
        context: Context,
        path: String,
    ): String? {
        val file = File(path)
        if (!file.exists()) {
            Timber.e("TestConfigReceiver: config file not found: $path")
            return null
        }
        if (!isInAppStorage(context, file)) {
            Timber.e("TestConfigReceiver: refusing to read config outside of the app storage: $path")
            return null
        }
        return runCatching { file.readText() }.getOrElse { ex ->
            Timber.e(ex, "TestConfigReceiver: failed to read config file: $path")
            null
        }
    }

    private fun isInAppStorage(
        context: Context,
        file: File,
    ): Boolean {
        val roots = listOfNotNull(context.getExternalFilesDir(null), context.filesDir)
        val canonical = runCatching { file.canonicalPath }.getOrNull() ?: return false
        return roots.any { root ->
            val rootPath = runCatching { root.canonicalPath }.getOrNull() ?: return@any false
            canonical == rootPath || canonical.startsWith("$rootPath${File.separator}")
        }
    }

    companion object {
        const val ACTION = "com.neilturner.aerialviews.testconfig.APPLY"
        const val EXTRA_JSON = "json"
        const val EXTRA_PATH = "path"
        const val EXTRA_PARTIAL = "partial"
    }
}
