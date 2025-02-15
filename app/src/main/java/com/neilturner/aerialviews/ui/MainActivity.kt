package com.neilturner.aerialviews.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.FirebaseHelper
import timber.log.Timber

class MainActivity :
    AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
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

        handleSharedFile(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSharedFile(intent)
    }

    private fun handleSharedFile(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: intent.data
                uri?.let { processAerialViewsFile(it) }
            }
        }
    }

    private fun processAerialViewsFile(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val text = inputStream.bufferedReader().use { it.readText() }
                Timber.i("Read content: $text")
                Toast.makeText(this, "Opened .aerialviews file!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Timber.e("Error reading file: ${e.message}")
            Toast.makeText(this, "Failed to open file", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("Main", this)
    }

    @SuppressLint("MissingSuperCall")
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putCharSequence("TITLE_TAG", title)
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
