package com.neilturner.aerialviews.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.FirebaseHelper
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity :
    AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    private val resultLauncher by lazy {
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val exitApp = result.data?.getBooleanExtra("exit_app", false)
                Timber.i("Exit app now? $exitApp")
                if (exitApp == true) {
                    finishAndRemoveTask()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.container, MainFragment())
            }
        } else {
            title = savedInstanceState.getCharSequence("TITLE_TAG")
        }

        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                setTitle(R.string.app_name)
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        lifecycleScope.launch {
            handleScreensaverOnLaunch()
        }
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("Main", this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putCharSequence("TITLE_TAG", title)
    }

    private fun handleScreensaverOnLaunch() {
        // Check if app was restarted by user (language change)
        val fromAppRestart = intent.getBooleanExtra("from_app_restart", false)

        // Check if app was started from intent
        val hasIntentUri = intent.data != null

        if (GeneralPrefs.startScreensaverOnLaunch &&
            !hasIntentUri &&
            !fromAppRestart
        ) {
            startScreensaver()
        }

        Timber.i(
            "fromAppRestart: $fromAppRestart, hasIntentUri: $hasIntentUri, startScreensaverOnLaunch: ${GeneralPrefs.startScreensaverOnLaunch}",
        )
    }

    private fun startScreensaver() {
        try {
            val intent = Intent().setClassName(applicationContext, "com.neilturner.aerialviews.ui.screensaver.TestActivity")
            resultLauncher.launch(intent)
        } catch (ex: Exception) {
            Timber.e(ex)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.popBackStackImmediate()) {
            return true
        }
        return super.onSupportNavigateUp()
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference,
    ): Boolean {
        val fragment =
            supportFragmentManager.fragmentFactory
                .instantiate(
                    classLoader,
                    pref.fragment.toString(),
                ).apply {
                    arguments = pref.extras
                }

        supportFragmentManager
            .commit {
                setCustomAnimations(
                    R.anim.slide_in,
                    R.anim.fade_out,
                    R.anim.fade_in,
                    R.anim.slide_out,
                )
                replace(R.id.container, fragment)
                    .addToBackStack(null)
            }.apply {
                title = pref.title
            }

        return true
    }
}
